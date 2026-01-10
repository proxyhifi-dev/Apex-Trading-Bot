package com.apex.backend.trading.pipeline;

import java.util.List;

public record SignalScore(
        boolean tradable,
        double score,
        String grade,
        double entryPrice,
        double suggestedStopLoss,
        String reason,
        FeatureVector featureVector,
        List<FeatureContribution> featureContributions
) {}
