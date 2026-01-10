package com.apex.backend.trading.pipeline;

import java.util.List;

public record RiskDecision(
        boolean allowed,
        double riskScore,
        List<String> reasons,
        double sizingMultiplier,
        int recommendedQuantity
) {}
