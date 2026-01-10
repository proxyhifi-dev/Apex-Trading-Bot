package com.apex.backend.dto;

import java.util.Map;

public record MultiTimeframeMomentumResult(
        double score,
        Map<String, Double> timeframeScores,
        double penaltyApplied
) {}
