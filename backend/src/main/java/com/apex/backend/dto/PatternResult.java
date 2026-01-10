package com.apex.backend.dto;

public record PatternResult(
        String type,
        boolean bullish,
        double strengthScore
) {}
