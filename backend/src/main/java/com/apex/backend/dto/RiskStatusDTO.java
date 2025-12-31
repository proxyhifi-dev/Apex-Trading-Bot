package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskStatusDTO {
    // Daily P&L tracking
    private double dailyPnL;
    private double dailyLossLimit;
    
    // Portfolio metrics
    private double portfolioValue;
    private double availableEquity;
    private int openPositions;
    
    // Risk status flags
    private boolean riskExceeded;
    private double remainingDailyLoss;
    private double maxDrawdown;
    
    // Circuit breaker status (for backward compatibility)
    private boolean isTradingHalted;
    private boolean isGlobalHalt;
    private boolean isEntryHalt;
    private int consecutiveLosses;
    private double dailyDrawdownPct;
}
