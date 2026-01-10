package com.apex.backend.dto;

public record BacktestResponse(
        Long id,
        String symbol,
        String timeframe,
        String metricsJson
) {}
