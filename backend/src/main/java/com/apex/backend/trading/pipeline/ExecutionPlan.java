package com.apex.backend.trading.pipeline;

import java.math.BigDecimal;

public record ExecutionPlan(
        ExecutionOrderType orderType,
        BigDecimal expectedFillPrice,
        BigDecimal expectedCost,
        double fillProbability,
        BigDecimal spreadCost,
        BigDecimal slippageCost,
        BigDecimal marketImpactCost,
        BigDecimal latencyCost
) {
    public enum ExecutionOrderType {
        MARKET,
        LIMIT
    }
}
