package com.apex.backend.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CircuitBreaker {

    private double dailyPnL = 0.0;
    private int consecutiveLosses = 0;

    // Limits
    private static final double MAX_DAILY_LOSS_PCT = 0.05; // 5%
    private static final int MAX_CONSECUTIVE_LOSSES = 4;

    public void recordTrade(double pnl) {
        this.dailyPnL += pnl;
        if (pnl < 0) {
            consecutiveLosses++;
        } else {
            consecutiveLosses = 0;
        }
    }

    public boolean canTrade(double currentEquity, double startingEquity) {
        // Check Daily Drawdown
        double maxLossAmount = startingEquity * MAX_DAILY_LOSS_PCT;
        if (dailyPnL <= -maxLossAmount) {
            log.warn("⛔ CIRCUIT BREAKER: Daily Loss Limit Hit ({})", dailyPnL);
            return false;
        }

        // Check Consecutive Losses
        if (consecutiveLosses >= MAX_CONSECUTIVE_LOSSES) {
            log.warn("⛔ CIRCUIT BREAKER: Max Consecutive Losses Hit ({})", consecutiveLosses);
            return false;
        }

        return true;
    }

    public void resetDailyStats() {
        this.dailyPnL = 0.0;
        this.consecutiveLosses = 0;
    }
}