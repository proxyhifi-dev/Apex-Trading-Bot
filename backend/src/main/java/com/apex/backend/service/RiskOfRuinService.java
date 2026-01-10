package com.apex.backend.service;

import org.springframework.stereotype.Service;

@Service
public class RiskOfRuinService {

    public double calculate(double winRate, double payoffRatio, double riskPerTradePct, double bankrollR) {
        if (winRate <= 0 || payoffRatio <= 0 || riskPerTradePct <= 0 || bankrollR <= 0) {
            return 1.0;
        }
        double lossRate = 1.0 - winRate;
        double edge = (winRate * payoffRatio) - lossRate;
        if (edge <= 0) {
            return 1.0;
        }
        double odds = lossRate / winRate;
        double exponent = bankrollR / riskPerTradePct;
        double ruin = Math.pow(odds, exponent);
        return Math.min(1.0, Math.max(0.0, ruin));
    }
}
