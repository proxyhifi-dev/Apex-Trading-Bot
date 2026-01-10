package com.apex.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record BacktestRequest(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        int bars
) {}
