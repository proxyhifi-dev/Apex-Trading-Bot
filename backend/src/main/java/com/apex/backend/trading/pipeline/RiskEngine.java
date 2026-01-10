package com.apex.backend.trading.pipeline;

public interface RiskEngine {
    RiskDecision evaluate(PipelineRequest request, SignalScore signalScore, PortfolioSnapshot snapshot);
}
