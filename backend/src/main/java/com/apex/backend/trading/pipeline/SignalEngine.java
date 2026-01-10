package com.apex.backend.trading.pipeline;

public interface SignalEngine {
    SignalScore score(PipelineRequest request);
}
