package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RiskManagementEngine {

    private final StrategyConfig config;

    private int consecutiveLosses = 0;
    private double dailyLossToday = 0;
    private LocalDate lastTradingDay = LocalDate.now();
    private final Map<String, Double> openPositions = new HashMap<>();

    public RiskManagementEngine(StrategyConfig config) {
        this.config = config;
    }

    public boolean canExecuteTrade(double currentEquity, String symbol, double entryPrice, double stopLoss, int quantity) {
        if (!lastTradingDay.equals(LocalDate.now())) {
            resetDailyLoss();
            lastTradingDay = LocalDate.now();
        }

        double minEquity = config.getRisk().getMinEquity();
        double dailyLossLimitPct = config.getRisk().getDailyLossLimitPct();
        int maxConsecutiveLosses = config.getRisk().getMaxConsecutiveLosses();
        double maxPositionLossPct = config.getRisk().getMaxPositionLossPct();

        if (currentEquity < minEquity) {
            log.warn("❌ Equity too low: ₹{} < ₹{}", currentEquity, minEquity);
            return false;
        }

        if (dailyLossToday < currentEquity * -dailyLossLimitPct) {
            log.warn("❌ Daily loss limit reached: ₹{}", dailyLossToday);
            return false;
        }

        if (consecutiveLosses >= maxConsecutiveLosses) {
            log.warn("❌ Max consecutive losses ({}) reached - STOP TRADING", consecutiveLosses);
            return false;
        }

        double positionRisk = Math.abs(entryPrice - stopLoss) * quantity;
        double riskPercent = positionRisk / currentEquity;

        if (riskPercent > maxPositionLossPct) {
            log.warn("❌ Position risk {} > max {} - Reduce qty", riskPercent, maxPositionLossPct);
            return false;
        }

        if (openPositions.containsKey(symbol)) {
            log.warn("❌ Already have open position in {}", symbol);
            return false;
        }

        log.info("✅ Trade approved: {} | Risk: {} | Daily Loss: {}", symbol, riskPercent, dailyLossToday);
        return true;
    }

    public void updateDailyLoss(double tradeResult) {
        dailyLossToday += tradeResult;
        if (tradeResult < 0) {
            consecutiveLosses++;
            log.warn("📉 Losing trade! Consecutive losses: {}", consecutiveLosses);
        } else {
            consecutiveLosses = 0;
            log.info("📈 Winning trade! Reset consecutive losses");
        }
    }

    public double getSlippageAdjustedPrice(double plannedPrice, boolean isBuy) {
        double slippagePct = config.getRisk().getSlippagePct();
        double adjustment = plannedPrice * slippagePct;
        return isBuy ? plannedPrice + adjustment : plannedPrice - adjustment;
    }

    public double getAdaptiveRiskPercent(double currentAtr, double averageAtr) {
        double volatilityRatio = currentAtr / averageAtr;
        if (volatilityRatio > 1.5) return 0.005;
        if (volatilityRatio < 0.7) return 0.015;
        return 0.010;
    }

    public void addOpenPosition(String symbol, double entryPrice) {
        openPositions.put(symbol, entryPrice);
    }

    public void removeOpenPosition(String symbol) {
        openPositions.remove(symbol);
    }

    // ✅ ADDED MISSING METHODS FOR CONTROLLER
    public boolean isTradingHalted(double currentEquity) {
        double dailyLossLimitPct = config.getRisk().getDailyLossLimitPct();
        int maxConsecutiveLosses = config.getRisk().getMaxConsecutiveLosses();

        boolean dailyLimitHit = dailyLossToday < (currentEquity * -dailyLossLimitPct);
        boolean streakHit = consecutiveLosses >= maxConsecutiveLosses;

        return dailyLimitHit || streakHit;
    }

    public int getConsecutiveLosses() {
        return consecutiveLosses;
    }

    public double getDailyLoss() {
        return dailyLossToday;
    }

    private void resetDailyLoss() {
        dailyLossToday = 0;
        consecutiveLosses = 0;
        log.info("🌅 New trading day - Reset daily loss");
    }
}