package com.apex.backend.trading.pipeline;

public interface PortfolioEngine {
    PortfolioSnapshot snapshot(PipelineRequest request);
}
