package com.apex.backend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.apex.backend.config.StrategyConfig;

@Component
@Slf4j
public class ExitEngine { // ✅ FIXED: Class name matches file name

    private final StrategyConfig strategyConfig;

    public ExitEngine(StrategyConfig strategyConfig) {
        this.strategyConfig = strategyConfig;
    }

    public ExitDecision manageTrade(TradeState state, double currentPrice,
                                    double currentAtr, boolean momentumWeakness) {

        state.setBarsInTrade(state.getBarsInTrade() + 1);

        if (currentPrice > state.getHighestPrice()) {
            state.setHighestPrice(currentPrice);
        }

        double entryPrice = state.getEntryPrice();
        double initialRisk = entryPrice - state.getInitialStopLoss();
        double currentProfit = currentPrice - entryPrice;
        double profitInR = currentProfit / initialRisk;

        double breakevenMoveR = strategyConfig.getRisk().getBreakevenMoveR();
        double breakevenOffsetR = strategyConfig.getRisk().getBreakevenOffsetR();
        double trailingStartR = strategyConfig.getRisk().getTrailingStartR();
        double trailingAtrMultiplier = strategyConfig.getRisk().getTrailingAtrMultiplier();
        double targetMultiplier = strategyConfig.getRisk().getTargetMultiplier();

        // 1. Breakeven after configured R
        if (!state.isBreakevenMoved() && profitInR >= breakevenMoveR) {
            state.setCurrentStopLoss(entryPrice + (initialRisk * breakevenOffsetR));
            state.setBreakevenMoved(true);
            log.info("🎯 BREAKEVEN MOVED: New SL @ ₹{}", state.getCurrentStopLoss());
        }

        // 2. Trailing Stop
        if (profitInR >= trailingStartR) {
            double trailDistance = currentAtr * trailingAtrMultiplier;
            double proposedStop = state.getHighestPrice() - trailDistance;
            if (proposedStop > state.getCurrentStopLoss()) {
                state.setCurrentStopLoss(proposedStop);
            }
        }

        if (momentumWeakness && profitInR >= strategyConfig.getRisk().getMomentumWeaknessTightenR()) {
            double tightenedStop = entryPrice + (initialRisk * strategyConfig.getRisk().getMomentumWeaknessStopOffsetR());
            if (tightenedStop > state.getCurrentStopLoss()) {
                state.setCurrentStopLoss(tightenedStop);
            }
        }

        // 3. Exit Checks
        if (currentPrice <= state.getCurrentStopLoss()) {
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), "STOP/TRAIL");
        }
        if (profitInR >= targetMultiplier) {
            return ExitDecision.exit(currentPrice, state.getCurrentStopLoss(), "TARGET");
        }

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
