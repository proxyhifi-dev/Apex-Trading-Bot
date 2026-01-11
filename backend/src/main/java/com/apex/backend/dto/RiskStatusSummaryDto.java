package com.apex.backend.dto;

/**
 * Lightweight risk status for the risk-status endpoint.
 * Named to avoid Windows case-insensitive filename collisions.
 */
public record RiskStatusSummaryDto(
        boolean tradingHalted,
        double portfolioHeat,
        int openPositions,
        int consecutiveLosses
) {}
