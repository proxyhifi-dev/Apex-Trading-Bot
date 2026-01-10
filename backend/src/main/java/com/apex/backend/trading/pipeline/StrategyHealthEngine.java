package com.apex.backend.trading.pipeline;

public interface StrategyHealthEngine {
    StrategyHealthDecision evaluate(Long userId);
}
