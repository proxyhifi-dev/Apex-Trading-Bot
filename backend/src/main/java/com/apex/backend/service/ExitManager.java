package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final StrategyConfig config;
    private final BroadcastService broadcastService; // ✅ Inject BroadcastService

    /**
     * Periodic automated exit management for open positions.
     * Checks: Target, Stop Loss, Trailing SL, Market Close Time
     */
    @Transactional
    public void manageExits() {
        List<Trade> openTrades = tradeRepository.findByStatus(Trade.TradeStatus.OPEN);
        if (openTrades.isEmpty()) return;

        boolean marketCloseTrigger = isMarketCloseTime();
        for (Trade trade : openTrades) {
            processTradeExit(trade, marketCloseTrigger);
        }
    }

    /**
     * Manual close triggered from Frontend UI.
     * Maps to: POST /api/positions/{id}/close
     */
    @Transactional
    public void manualClose(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found with ID: " + tradeId));

        if (trade.getStatus() == Trade.TradeStatus.CLOSED) {
            log.warn("⚠️ Attempted to manually close already closed trade: {}", trade.getSymbol());
            return;
        }

        log.info("🎯 Manual Exit Triggered for: {}", trade.getSymbol());
        double ltp = fyersService.getLTP(trade.getSymbol());

        // Fallback LTP if API fails
        if (ltp == 0) {
            ltp = trade.getEntryPrice();
            log.warn("⚠️ Using Entry Price for manual exit as LTP unavailable for {}", trade.getSymbol());
        }

        closeTrade(trade, ltp, Trade.ExitReason.MANUAL);
    }

    /**
     * Process individual trade exit based on current market conditions
     */
    private void processTradeExit(Trade trade, boolean forceExit) {
        try {
            double ltp = fyersService.getLTP(trade.getSymbol());
            if (ltp == 0) {
                log.warn("⚠️ LTP unavailable for {}, skipping exit check", trade.getSymbol());
                return;
            }

            // Track highest price for trailing logic
            if (trade.getHighestPrice() == null || ltp > trade.getHighestPrice()) {
                trade.setHighestPrice(ltp);
                tradeRepository.save(trade);
            }

            // 1. Forced Market-Close Exit (15:15+)
            if (forceExit) {
                closeTrade(trade, ltp, Trade.ExitReason.TIME_EXIT);
                return;
            }

            // 2. Dynamic Target Check (3x ATR or config-based)
            double atr = (trade.getAtr() != null) ? trade.getAtr() : (trade.getEntryPrice() * 0.01);
            double targetPrice = trade.getEntryPrice() + (config.getRisk().getTargetMultiplier() * atr);

            if (ltp >= targetPrice) {
                closeTrade(trade, ltp, Trade.ExitReason.TARGET);
                return;
            }

            // 3. Stop Loss Check
            if (ltp <= trade.getCurrentStopLoss()) {
                closeTrade(trade, ltp, Trade.ExitReason.STOP_LOSS);
                return;
            }

            // 4. Trailing Stop Loss Update
            updateTrailingStop(trade, ltp);

        } catch (Exception e) {
            log.error("❌ Error processing exit for {}: {}", trade.getSymbol(), e.getMessage());
        }
    }

    /**
     * Update trailing stop loss when price moves favorably
     */
    private void updateTrailingStop(Trade trade, double ltp) {
        double risk = Math.abs(trade.getEntryPrice() - trade.getStopLoss());
        double profitThreshold = trade.getEntryPrice() + (1.5 * risk); // 1.5R profit

        if (trade.getHighestPrice() >= profitThreshold) {
            double newSl = trade.getEntryPrice() + (0.1 * risk); // Move to breakeven+

            if (newSl > trade.getCurrentStopLoss()) {
                trade.setCurrentStopLoss(newSl);
                tradeRepository.save(trade);

                log.info("🪜 Trailing SL Updated for {}: ₹{} -> ₹{}",
                        trade.getSymbol(), trade.getStopLoss(), newSl);

                // ✅ Broadcast updated position to frontend
                broadcastService.broadcastPositions(
                        tradeRepository.findByStatus(Trade.TradeStatus.OPEN)
                );
            }
        }
    }

    /**
     * Close trade with proper execution (paper or live)
     */
    private void closeTrade(Trade trade, double rawExitPrice, Trade.ExitReason reason) {
        double finalExitPrice = rawExitPrice;

        if (trade.isPaperTrade()) {
            // Apply slippage for realistic paper trading
            double slippage = rawExitPrice * config.getRisk().getSlippagePct();
            finalExitPrice = rawExitPrice - slippage;
            log.info("📝 Paper Exit | Slippage Applied: ₹{} -> ₹{}", rawExitPrice, finalExitPrice);
        } else {
            // Live Execution: Send SELL order to Fyers
            try {
                String orderId = fyersService.placeOrder(
                        trade.getSymbol(),
                        trade.getQuantity(),
                        "SELL",
                        "MARKET",
                        0
                );
                log.info("✅ Live Market SELL Order Executed: ID={}", orderId);
            } catch (Exception e) {
                log.error("❌ Live Exit Failed for {}: {}", trade.getSymbol(), e.getMessage());
                throw new RuntimeException("Broker execution failed: " + e.getMessage());
            }
        }

        // Calculate realized P&L
        double pnl = (finalExitPrice - trade.getEntryPrice()) * trade.getQuantity();

        // Update trade record
        trade.setExitPrice(finalExitPrice);
        trade.setExitTime(LocalDateTime.now());
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitReason(reason);
        trade.setRealizedPnl(pnl);

        tradeRepository.save(trade);

        // Update risk engine
        riskEngine.updateDailyLoss(pnl);
        riskEngine.removeOpenPosition(trade.getSymbol());

        log.info("🏁 Trade Closed: {} | Reason: {} | Realized P&L: ₹{}",
                trade.getSymbol(), reason, String.format("%.2f", pnl));

        // ✅ Broadcast updated positions to frontend
        broadcastService.broadcastPositions(
                tradeRepository.findByStatus(Trade.TradeStatus.OPEN)
        );

        log.info("📢 Broadcasted updated positions to UI after closing {}", trade.getSymbol());
    }

    /**
     * Check if market is about to close (square-off time)
     */
    private boolean isMarketCloseTime() {
        // Square off intraday positions before 15:15 IST
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(15, 14));
    }

    /**
     * Get count of open positions (for monitoring)
     */
    public long getOpenPositionCount() {
        return tradeRepository.countByStatus(Trade.TradeStatus.OPEN);
    }
}
