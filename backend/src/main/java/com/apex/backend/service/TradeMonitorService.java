package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeMonitorService {

    private final TradeRepository tradeRepo;
    private final FyersService fyersService;
    private final IndicatorEngine indicatorEngine;
    private final ExitEngine exitEngine;
    private final RiskManagementEngine riskEngine;  // ADDED

    /**
     * Runs every 60 seconds to monitor all open trades
     * and apply exit logic (breakeven, trailing, time stop)
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void monitorOpenTrades() {
        List<Trade> openTrades = tradeRepo.findByStatus(Trade.TradeStatus.OPEN);

        if (openTrades.isEmpty()) {
            return;
        }

        log.info("🔍 Monitoring {} open trades...", openTrades.size());

        for (Trade trade : openTrades) {
            try {
                checkAndUpdateTrade(trade);
            } catch (Exception e) {
                log.error("❌ Error monitoring trade {}: {}", trade.getId(), e.getMessage());
            }
        }
    }

    private void checkAndUpdateTrade(Trade trade) {
        // 1) Get current price
        List<Candle> history = fyersService.getHistoricalData(trade.getSymbol(), 50);
        if (history.isEmpty()) {
            log.warn("⚠️ No data for {}", trade.getSymbol());
            return;
        }

        Candle latestCandle = history.get(history.size() - 1);
        double currentPrice = latestCandle.getClose();

        // 2) Calculate current ATR
        double currentAtr = indicatorEngine.calculateATR(history, 10);

        // 3) Check momentum weakness (optional)
        IndicatorEngine.MacdResult macd = indicatorEngine.calculateMACD(history);
        boolean momentumWeakness = !macd.isBullish();

        // 4) Build exit state from trade
        ExitEngine.TradeState state = new ExitEngine.TradeState(
                trade.getEntryPrice(),
                trade.getStopLoss(),
                trade.getQuantity()
        );
        state.setCurrentStopLoss(trade.getCurrentStopLoss());
        state.setHighestPrice(trade.getHighestPrice());
        state.setBarsInTrade(trade.getBarsInTrade());
        state.setBreakevenMoved(trade.isBreakevenMoved());

        // 5) Apply exit logic
        ExitEngine.ExitDecision decision = exitEngine.manageTrade(
                state,
                currentPrice,
                currentAtr,
                momentumWeakness
        );

        // 6) Update trade entity
        trade.setBarsInTrade(state.getBarsInTrade());
        trade.setHighestPrice(state.getHighestPrice());
        trade.setBreakevenMoved(state.isBreakevenMoved());
        trade.setCurrentStopLoss(decision.getNewStopLoss());

        if (decision.isShouldExit()) {
            exitTrade(trade, decision);
        } else {
            tradeRepo.save(trade);
            log.debug("✅ Updated trade {} | SL: {} | Bars: {}",
                    trade.getSymbol(), trade.getCurrentStopLoss(), trade.getBarsInTrade());
        }
    }

    private void exitTrade(Trade trade, ExitEngine.ExitDecision decision) {
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitPrice(decision.getExitPrice());
        trade.setExitTime(LocalDateTime.now());
        trade.setExitReason(Trade.ExitReason.valueOf(decision.getReason()));

        // ✅ FIX 1: Calculate and SAVE PnL
        double profit = (decision.getExitPrice() - trade.getEntryPrice()) * trade.getQuantity();
        double profitPercent = ((decision.getExitPrice() - trade.getEntryPrice()) / trade.getEntryPrice()) * 100;

        trade.setPnl(profit);
        trade.setPnlPercent(profitPercent);

        tradeRepo.save(trade);

        // ✅ FIX 2: Update Risk Engine with trade result
        riskEngine.updateDailyLoss(profit);
        riskEngine.removeOpenPosition(trade.getSymbol());

        log.info("🚪 EXIT: {} | Reason: {} | Entry: ₹{} | Exit: ₹{} | P&L: ₹{} ({:.2f}%) | Bars: {}",
                trade.getSymbol(),
                decision.getReason(),
                trade.getEntryPrice(),
                decision.getExitPrice(),
                String.format("%.2f", profit),
                profitPercent,
                trade.getBarsInTrade()
        );

        // Optional: place actual sell order
        if (!trade.isPaperTrade()) {
            fyersService.placeOrder(
                    trade.getSymbol(),
                    trade.getQuantity(),
                    "SELL",
                    "MARKET",
                    0.0,
                    false
            );
        }
    }
}
