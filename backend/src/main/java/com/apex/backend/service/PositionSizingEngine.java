package com.apex.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PositionSizingEngine {

    private static final double DEFAULT_RISK_PCT = 0.01;
    private static final double HIGH_SCORE_BOOST = 1.3;
    private static final double LOW_SCORE_REDUCTION = 0.8;
    private static final double MAX_POSITION_PCT = 0.25;

    /**
     * Intelligent position sizing with all safety checks
     */
    public int calculateQuantityIntelligent(
            double portfolioEquity,
            double entryPrice,
            double stopLoss,
            int signalScore,
            double currentAtr,
            double averageAtr,
            RiskManagementEngine riskMgmt) {  // ADDED THIS PARAMETER

        log.info("📊 Calculating position size - Equity: {}, Entry: {}, SL: {}, Score: {}",
                portfolioEquity, entryPrice, stopLoss, signalScore);

        // Step 1: Get adaptive risk based on volatility
        double adaptiveRisk = riskMgmt.getAdaptiveRiskPercent(currentAtr, averageAtr);
        log.info("📊 Adaptive risk: {}%", adaptiveRisk * 100);

        // Step 2: Account for slippage
        double slippageAdjustedEntry = riskMgmt.getSlippageAdjustedPrice(entryPrice, true);
        double riskPerShare = Math.abs(slippageAdjustedEntry - stopLoss);

        if (riskPerShare < 0.01) {
            log.warn("⚠️ Risk per share too low: {}", riskPerShare);
            return 0;
        }

        // Step 3: Calculate base quantity
        double amountToRisk = portfolioEquity * adaptiveRisk;
        int baseQty = (int) (amountToRisk / riskPerShare);
        log.info("📊 Base quantity: {} | Risk: ₹{}", baseQty, amountToRisk);

        // Step 4: Apply confidence boost for high-score signals
        int boostedQty = baseQty;
        if (signalScore >= 85) {
            boostedQty = (int) (baseQty * HIGH_SCORE_BOOST);
            log.info("🚀 High confidence boost (score: {}) applied: {} → {}", signalScore, baseQty, boostedQty);
        } else if (signalScore < 75) {
            boostedQty = (int) (baseQty * LOW_SCORE_REDUCTION);
            log.info("⚠️ Low confidence reduction (score: {}) applied: {} → {}", signalScore, baseQty, boostedQty);
        }

        // Step 5: Apply maximum position cap (25% of portfolio)
        int maxQty = (int) ((portfolioEquity * MAX_POSITION_PCT) / slippageAdjustedEntry);
        int finalQty = Math.min(boostedQty, maxQty);

        log.info("✅ FINAL POSITION SIZE: {} shares | Risk: ₹{} | Max Cap: {}",
                finalQty, amountToRisk, maxQty);

        return finalQty;
    }

    /**
     * Legacy method - kept for backwards compatibility
     */
    public int calculateQuantity(double portfolioEquity, double entryPrice, double stopLoss, int score) {
        double amountToRisk = portfolioEquity * DEFAULT_RISK_PCT;
        double riskPerShare = Math.abs(entryPrice - stopLoss);

        if (riskPerShare < 0.01) {
            log.warn("⚠️ Risk per share too low - returning 0 qty");
            return 0;
        }

        int qty = (int) (amountToRisk / riskPerShare);

        if (score >= 85) {
            qty = (int) (qty * HIGH_SCORE_BOOST);
            log.info("🚀 High score boost applied - Score: {} | Qty: {}", score, qty);
        }

        int maxQty = (int) ((portfolioEquity * MAX_POSITION_PCT) / entryPrice);
        int finalQty = Math.min(qty, maxQty);

        log.info("📊 Position Size - Risk: ₹{}, Qty: {}, Max: {}, Final: {}",
                amountToRisk, qty, maxQty, finalQty);

        return finalQty;
    }
}
