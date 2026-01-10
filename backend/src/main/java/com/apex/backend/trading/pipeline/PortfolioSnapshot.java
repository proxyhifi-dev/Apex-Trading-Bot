package com.apex.backend.trading.pipeline;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PortfolioSnapshot(
        BigDecimal equity,
        double heat,
        Map<String, Double> correlations,
        List<String> openSymbols
) {}
