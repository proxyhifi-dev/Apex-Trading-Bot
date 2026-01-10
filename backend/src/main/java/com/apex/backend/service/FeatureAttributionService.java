package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.service.StrategyScoringService.ScoreBreakdown;
import com.apex.backend.trading.pipeline.FeatureContribution;
import com.apex.backend.trading.pipeline.FeatureVector;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureAttributionService {

    public FeatureVector buildFeatureVector(ScoreBreakdown breakdown) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        normalized.put("momentum", normalize(breakdown.momentumScore(), breakdown.totalScore()));
        normalized.put("trend", normalize(breakdown.trendScore(), breakdown.totalScore()));
        normalized.put("rsi", normalize(breakdown.rsiScore(), breakdown.totalScore()));
        normalized.put("volatility", normalize(breakdown.volatilityScore(), breakdown.totalScore()));
        normalized.put("squeeze", normalize(breakdown.squeezeScore(), breakdown.totalScore()));
        return new FeatureVector(normalized);
    }

    public List<FeatureContribution> computeContributions(FeatureVector vector, StrategyProperties.Scoring weights) {
        List<FeatureContribution> contributions = new ArrayList<>();
        contributions.add(build("momentum", vector, weights.getMomentumWeight()));
        contributions.add(build("trend", vector, weights.getTrendWeight()));
        contributions.add(build("rsi", vector, weights.getRsiWeight()));
        contributions.add(build("volatility", vector, weights.getVolatilityWeight()));
        contributions.add(build("squeeze", vector, weights.getSqueezeWeight()));
        return contributions;
    }

    private FeatureContribution build(String feature, FeatureVector vector, double weight) {
        double normalizedValue = vector.normalizedValues().getOrDefault(feature, 0.0);
        double contribution = normalizedValue * weight;
        return new FeatureContribution(feature, normalizedValue, weight, contribution);
    }

    private double normalize(double score, double totalScore) {
        if (totalScore == 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, score / totalScore));
    }
}
