package com.apex.backend.trading.pipeline;

public record FeatureContribution(
        String feature,
        double normalizedValue,
        double weight,
        double contribution
) {}
