package com.apex.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationServiceTest {

    @Test
    void correlationUsesReturnsWhenPricesScaled() {
        CorrelationService service = new CorrelationService();
        List<Double> base = List.of(100.0, 101.0, 99.0, 102.0, 103.0, 101.0, 104.0, 105.0);
        List<Double> scaled = List.of(200.0, 202.0, 198.0, 204.0, 206.0, 202.0, 208.0, 210.0);

        double correlation = service.calculateCorrelation(base, scaled);

        assertTrue(correlation > 0.99);
    }
}
