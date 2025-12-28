package com.apex.backend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ExitEngine {

    /**
     * ✅ OPTIMIZATION: Intelligent exit management
     * - Breakeven after 1R profit
     * - Trailing stop tightens with profit
     * - Partial profit taking at milestones
     */
    public ExitDecision manageTrade(TradeState state, double currentPrice,
                                    double currentAtr, boolean momentumWeakness) {

        state.setBarsInTrade(state.getBarsInTrade() + 1);

        // Track highest price
        if (currentPrice > state.getHighestPrice()) {
            state.setHighestPrice(currentPrice);
        }

        double entryPrice = state.getEntryPrice();
        double initialRisk = entryPrice - state.getInitialStopLoss();
        double currentProfit = currentPrice - entryPrice;
        double profitInR = currentProfit / initialRisk;  // Profit in R units

        // ✅ OPTIMIZATION 1: Breakeven after 1R profit
        if (!state.isBreakevenMoved() && profitInR >= 1.0) {
            state.setCurrentStopLoss(entryPrice + (initialRisk * 0.1));  // 0.1R profit locked
            state.setBreakevenMoved(true);
            log.info("🎯 BREAKEVEN MOVED: New SL @ ₹{} (locked 0.1R profit)", state.getCurrentStopLoss());
        }

        // ✅ OPTIMIZATION 2: Aggressive trailing after 2R profit
        if (profitInR >= 2.0) {
            double trailDistance = currentAtr * 1.5;  // Tighter trail (1.5 ATR)
            double proposedStop = state.getHighestPrice() - trailDistance;

            if (proposedStop > state.getCurrentStopLoss()) {
                state.setCurrentStopLoss(proposedStop);
                log.info("📈 TRAILING STOP: New SL @ ₹{} (2R+ profit)", proposedStop);
            }
        } else if (state.isBreakevenMoved()) {
            // Normal trailing after breakeven
            double trailDistance = currentAtr * 2.0;
            double proposedStop = state.getHighestPrice() - trailDistance;

            if (proposedStop > state.getCurrentStopLoss()) {
                state.setCurrentStopLoss(proposedStop);
            }
        }

        // ✅ Exit Check 1: Stop-loss hit
        if (currentPrice <= state.getCurrentStopLoss()) {
            String reason = state.isBreakevenMoved() ? "TRAILING_STOP" : "STOP_LOSS";
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), reason);
        }

        // ✅ Exit Check 2: Momentum weakness after 2R profit
        if (momentumWeakness && profitInR >= 2.0) {
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), "MACD_EXIT");
        }

        // ✅ Exit Check 3: Time stop (no profit after 10 bars)
        if (state.getBarsInTrade() >= 10 && profitInR < 0.5) {
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), "TIME_STOP");
        }

        // ✅ Exit Check 4: Target hit (3R profit - take profits)
        if (profitInR >= 3.0) {
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), "TARGET");
        }

        // Hold position
        return ExitDecision.hold(state.getCurrentStopLoss());
    }

    @Data
    public static class TradeState {
        private final double entryPrice;
        private final double initialStopLoss;
        private final int quantity;

        private double currentStopLoss;
        private double highestPrice;
        private int barsInTrade;
        private boolean breakevenMoved;

        public TradeState(double entryPrice, double initialStopLoss, int quantity) {
            this.entryPrice = entryPrice;
            this.initialStopLoss = initialStopLoss;
            this.quantity = quantity;
            this.currentStopLoss = initialStopLoss;
            this.highestPrice = entryPrice;
            this.barsInTrade = 0;
            this.breakevenMoved = false;
        }
    }

    @Data
    public static class ExitDecision {
        private final boolean shouldExit;
        private final double exitPrice;
        private final double newStopLoss;
        private final String reason;

        public static ExitDecision exit(double exitPrice, double stopLoss, String reason) {
            return new ExitDecision(true, exitPrice, stopLoss, reason);
        }

        public static ExitDecision hold(double newStopLoss) {
            return new ExitDecision(false, 0, newStopLoss, "HOLD");
        }
    }
}
