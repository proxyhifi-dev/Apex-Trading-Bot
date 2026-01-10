package com.apex.backend.dto;

public record TradeAttributionResponse(
        String feature,
        double normalizedValue,
        double weight,
        double contribution
) {}
