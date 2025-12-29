package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitManager {

    private final TradeRepository tradeRepository;
    private final FyersService fyersService;
    private final RiskManagementEngine riskEngine;
    private final StrategyConfig config; // ✅ Added config for slippage

    @Transactional
    public void manageExits() {
        List<Trade> openTrades = tradeRepository.findByStatus(Trade.TradeStatus.OPEN);
        if (openTrades.isEmpty()) return;

        boolean marketCloseTrigger = isMarketCloseTime();
        for (Trade trade : openTrades) {
            processTradeExit(trade, marketCloseTrigger);
        }
    }

    private void processTradeExit(Trade trade, boolean forceExit) {
        try {
            double ltp = fyersService.getLTP(trade.getSymbol());
            if (ltp == 0) return;

            if (trade.getHighestPrice() == null || ltp > trade.getHighestPrice()) {
                trade.setHighestPrice(ltp);
                tradeRepository.save(trade);
            }

            if (forceExit) {
                closeTrade(trade, ltp, Trade.ExitReason.TIME_EXIT);
                return;
            }

            // Fallback ATR if missing
            double atr = (trade.getAtr() != null) ? trade.getAtr() : (trade.getEntryPrice() * 0.01);

            // 1. Target (3x ATR)
            if (ltp >= trade.getEntryPrice() + (3.0 * atr)) {
                closeTrade(trade, ltp, Trade.ExitReason.TARGET);
                return;
            }

            // 2. Stop Loss
            if (ltp <= trade.getCurrentStopLoss()) {
                closeTrade(trade, ltp, Trade.ExitReason.STOP_LOSS);
                return;
            }

            // 3. Trailing (Simple 1.5R Logic)
            double risk = Math.abs(trade.getEntryPrice() - trade.getStopLoss());
            if (trade.getHighestPrice() >= trade.getEntryPrice() + (1.5 * risk)) {
                double newSl = trade.getEntryPrice() + (0.1 * risk); // Move to Breakeven+
                if (newSl > trade.getCurrentStopLoss()) {
                    trade.setCurrentStopLoss(newSl);
                    tradeRepository.save(trade);
                    log.info("🪜 Trailing SL Updated: {}", newSl);
                }
            }

        } catch (Exception e) {
            log.error("Error managing trade {}: {}", trade.getSymbol(), e.getMessage());
        }
    }

    private void closeTrade(Trade trade, double rawExitPrice, Trade.ExitReason reason) {
        double finalExitPrice = rawExitPrice;

        if (trade.isPaperTrade()) {
            double slippage = rawExitPrice * config.getRisk().getSlippagePct();
            finalExitPrice = rawExitPrice - slippage;
            log.info("📝 Paper Slippage: {} -> {}", rawExitPrice, finalExitPrice);
        } else {
            try {
                fyersService.placeOrder(trade.getSymbol(), trade.getQuantity(), "SELL", "MARKET", 0);
            } catch (Exception e) {
                log.error("❌ Close Failed: {}", e.getMessage());
            }
        }

        double pnl = (finalExitPrice - trade.getEntryPrice()) * trade.getQuantity();

        trade.setExitPrice(finalExitPrice);
        trade.setExitTime(LocalDateTime.now());
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitReason(reason);
        trade.setRealizedPnl(pnl);

        tradeRepository.save(trade);
        riskEngine.updateDailyLoss(pnl);
        riskEngine.removeOpenPosition(trade.getSymbol());

        log.info("✅ Closed {}: PnL={}", trade.getSymbol(), pnl);
    }

    private boolean isMarketCloseTime() {
        return LocalTime.now().isAfter(LocalTime.of(15, 14));
    }
}