package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
@RequiredArgsConstructor
public class PositionSizingEngine {

    private final StrategyConfig config;

    public int calculateQuantityIntelligent(
            BigDecimal currentEquity,
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            String grade,           // ✅ Grade (A, A++, etc)
            double currentVix,      // ✅ VIX Input
            RiskManagementEngine riskMgmt) {

        // 1. Minimum Capital Check (₹50,000)
        BigDecimal minEquity = BigDecimal.valueOf(config.getRisk().getMinEquity());
        if (currentEquity.compareTo(minEquity) < 0) {
            log.warn("⛔ Sizing: Insufficient Capital (₹{} < ₹50,000). Trade Skipped.", currentEquity.intValue());
            return 0;
        }

        // 2. Base Risk (1% default)
        double riskPercent = config.getRisk().getMaxPositionLossPct();
        log.info("🔹 Base Risk: {}%", riskPercent * 100);

        // 3. Grade Multiplier
        double gradeMultiplier = 1.0;
        switch (grade) {
            case "A+++": gradeMultiplier = 2.0; break;
            case "A++":  gradeMultiplier = 1.5; break;
            case "A+":
            case "A":    gradeMultiplier = 1.0; break;
            default:     gradeMultiplier = 0.75; // Penalize B/C grades
        }
        riskPercent *= gradeMultiplier;
        log.info("📊 Grade '{}' Multiplier: {}x -> New Risk: {}%", grade, gradeMultiplier, String.format("%.2f", riskPercent * 100));

        // 4. Dynamic Risk Adjustment (After Losses)
        if (riskMgmt.getConsecutiveLosses() > 0) {
            riskPercent *= 0.75;
            log.info("📉 Consecutive Losses Detected ({}). Reducing Risk by 0.75x -> {}%", riskMgmt.getConsecutiveLosses(), String.format("%.2f", riskPercent * 100));
        }

        // 5. Safety Cut (High VIX or High Daily Drawdown)
        double currentDailyLossPct = riskMgmt.getDailyLossPercent(currentEquity.doubleValue());
        boolean highVix = currentVix > 25.0;
        boolean highDrawdown = currentDailyLossPct > 3.0; // 3% Loss

        if (highVix || highDrawdown) {
            riskPercent *= 0.50; // 50% Safety Cut
            log.warn("⚠️ High Risk Mode (VIX: {} | DD: {}%). Safety Cut 50% -> Final Risk: {}%", currentVix, currentDailyLossPct, String.format("%.2f", riskPercent * 100));
        }

        // --- Calculate Quantity ---
        BigDecimal maxRiskAmount = currentEquity.multiply(BigDecimal.valueOf(riskPercent));
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss).abs();

        if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) {
            riskPerShare = entryPrice.multiply(BigDecimal.valueOf(0.01));
        }

        int quantity = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.DOWN).intValue();

        // 6. Max Capital Allocation Check (Max 35% per trade)
        BigDecimal maxAllocatedCapital = currentEquity.multiply(BigDecimal.valueOf(config.getRisk().getMaxSingleTradeCapitalPct())); // 35%
        int maxQtyByCapital = maxAllocatedCapital.divide(entryPrice, 0, RoundingMode.DOWN).intValue();

        if (quantity > maxQtyByCapital) {
            log.info("🔒 Quantity Capped by Max Capital (35%). Reduced {} -> {}", quantity, maxQtyByCapital);
            quantity = maxQtyByCapital;
        }

        if (quantity < 1) quantity = 0;

        BigDecimal riskAmount = riskPerShare.multiply(BigDecimal.valueOf(quantity));
        log.info("🧮 Final Sizing: Qty={} | Risk Amt=₹{} | Eq=₹{}", quantity, MoneyUtils.scale(riskAmount), MoneyUtils.scale(currentEquity));
        return quantity;
    }
}
