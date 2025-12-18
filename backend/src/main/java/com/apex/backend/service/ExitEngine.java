package com.apex.backend.service;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class ExitEngine {

    @Data
    public static class TradeState {
        double entryPrice;
        double initialStopLoss;
        double currentStopLoss;
        double highestPrice;
        int qty;
        boolean breakevenMoved = false;

        public TradeState(double entry, double sl, int q) {
            this.entryPrice = entry;
            this.initialStopLoss = sl;
            this.currentStopLoss = sl;
            this.highestPrice = entry;
            this.qty = q;
        }

        public double getRisk() {
            return Math.abs(entryPrice - initialStopLoss);
        }
    }

    @Data
    public static class ExitDecision {
        boolean shouldExit;
        String reason;
        double exitPrice;

        public static ExitDecision hold() {
            ExitDecision d = new ExitDecision();
            d.shouldExit = false;
            return d;
        }

        public static ExitDecision exit(String reason, double price) {
            ExitDecision d = new ExitDecision();
            d.shouldExit = true;
            d.reason = reason;
            d.exitPrice = price;
            return d;
        }
    }

    public ExitDecision manageTrade(TradeState state, double currentPrice, double currentAtr, boolean momentumWeakness) {
        // 1. Update High Water Mark
        if (currentPrice > state.highestPrice) {
            state.highestPrice = currentPrice;
        }

        double risk = state.getRisk();
        double currentProfitPerShare = currentPrice - state.entryPrice;

        // 2. Breakeven Logic (Move SL to Entry after 1R profit)
        if (!state.breakevenMoved && currentProfitPerShare >= risk) {
            state.currentStopLoss = state.entryPrice;
            state.breakevenMoved = true;
        }

        // 3. Trailing Stop Logic (Tighten trail after 2R profit)
        if (currentProfitPerShare >= (2.0 * risk)) {
            double tightStop = state.highestPrice - (1.5 * currentAtr); // Trail by 1.5 ATR
            if (tightStop > state.currentStopLoss) {
                state.currentStopLoss = tightStop;
            }
        }

        // 4. Check Exits

        // Hard Stop / Trailing Stop Hit
        if (currentPrice <= state.currentStopLoss) {
            return ExitDecision.exit("STOP_HIT", state.currentStopLoss);
        }

        // Momentum Failure (MACD Cross down)
        if (momentumWeakness) {
            return ExitDecision.exit("MOMENTUM_FAIL", currentPrice);
        }

        return ExitDecision.hold();
    }
}