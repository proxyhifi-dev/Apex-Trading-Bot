package com.apex.backend.dto;

import java.time.LocalDateTime;

public record BacktestRunSummary(
        Long id,
        String symbol,
        String timeframe,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {}
