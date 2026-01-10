package com.apex.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskMetricsServiceTest {

    @Test
    void calculatesRiskOfRuin() {
        RiskOfRuinService service = new RiskOfRuinService();
        double winRate = 0.6;
        double payoffRatio = 1.5;
        double riskPerTrade = 0.01;
        double bankrollR = 50.0;

        double lossRate = 1.0 - winRate;
        double expected = Math.pow(lossRate / winRate, bankrollR / riskPerTrade);

        double ruin = service.calculate(winRate, payoffRatio, riskPerTrade, bankrollR);

        assertThat(ruin).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void calculatesCvarAtConfidence() {
        CvarService service = new CvarService();
        List<Double> returns = List.of(-0.1, -0.05, 0.02, 0.03);

        double cvar = service.calculate(returns, 0.95);

        assertThat(cvar).isCloseTo(-0.1, org.assertj.core.data.Offset.offset(0.0001));
    }
}
