package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Comprehensive risk snapshot used by services.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskStatusDto {
    private double dailyPnL;
    private double dailyLossLimit;
    private double portfolioValue;
    private double availableEquity;
    private int openPositions;
    private boolean riskExceeded;
    private double remainingDailyLoss;
}
