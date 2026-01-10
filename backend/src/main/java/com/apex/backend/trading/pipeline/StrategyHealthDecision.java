package com.apex.backend.trading.pipeline;

import java.util.List;

public record StrategyHealthDecision(
        StrategyHealthStatus status,
        List<String> reasons
) {
    public enum StrategyHealthStatus {
        HEALTHY,
        WARNING,
        BROKEN
    }
}
