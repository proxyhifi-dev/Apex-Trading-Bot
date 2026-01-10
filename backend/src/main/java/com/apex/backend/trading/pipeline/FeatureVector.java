package com.apex.backend.trading.pipeline;

import java.util.Map;

public record FeatureVector(Map<String, Double> normalizedValues) {}
