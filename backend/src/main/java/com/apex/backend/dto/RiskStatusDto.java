package com.apex.backend.dto;

public record RiskStatusDto(
        boolean tradingHalted,
        double portfolioHeat,
        int openPositions,
        int consecutiveLosses
) {}
