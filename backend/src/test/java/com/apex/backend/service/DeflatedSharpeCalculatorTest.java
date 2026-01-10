package com.apex.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeflatedSharpeCalculatorTest {

    @Test
    void penalizesSharpeForMultipleTrials() {
        DeflatedSharpeCalculator calculator = new DeflatedSharpeCalculator();
        double sharpe = 2.0;
        double expectedPenalty = Math.sqrt(2.0 * Math.log(50) / 99.0);

        double deflated = calculator.calculate(sharpe, 100, 50);

        assertThat(deflated).isCloseTo(sharpe - expectedPenalty, org.assertj.core.data.Offset.offset(0.0001));
    }
}
