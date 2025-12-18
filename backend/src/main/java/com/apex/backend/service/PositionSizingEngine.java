package com.apex.backend.service;

import org.springframework.stereotype.Component;

@Component
public class PositionSizingEngine {

    public int calculateQuantity(double equity, double entryPrice, double stopLoss, int qualityScore) {
        double riskPerShare = Math.abs(entryPrice - stopLoss);
        if (riskPerShare <= 0) return 0;

        // Dynamic Risk % based on Quality Score (0-100)
        double riskPct;
        if (qualityScore >= 80) {
            riskPct = 0.015; // 1.5% for A+ setups
        } else if (qualityScore >= 60) {
            riskPct = 0.010; // 1.0% for Standard setups
        } else {
            riskPct = 0.005; // 0.5% for C-grade setups
        }

        double maxRiskAmount = equity * riskPct;
        int quantity = (int) Math.floor(maxRiskAmount / riskPerShare);

        return Math.max(1, quantity); // Ensure at least 1 qty if trade is valid
    }
}