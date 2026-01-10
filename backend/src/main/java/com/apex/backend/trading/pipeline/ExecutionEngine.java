package com.apex.backend.trading.pipeline;

public interface ExecutionEngine {
    ExecutionPlan build(PipelineRequest request, SignalScore signalScore, RiskDecision riskDecision);
}
