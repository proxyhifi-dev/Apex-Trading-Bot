package com.apex.backend.trading.pipeline;

import java.util.List;

public record DecisionResult(
        String symbol,
        DecisionAction action,
        double score,
        List<String> reasons,
        RiskDecision riskDecision,
        ExecutionPlan executionPlan,
        SignalScore signalScore,
        StrategyHealthDecision strategyHealthDecision
) {
    public enum DecisionAction {
        BUY,
        SELL,
        HOLD
    }
}
