package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiskStatusResponse {

    private double dailyLoss;              // Current day's realized P&L (can be negative)
    private double dailyLossLimitPct;      // e.g. 0.05 for 5%
    private int consecutiveLosses;         // Current streak of losing trades
    private int maxConsecutiveLosses;      // Limit before blocking trades
    private boolean tradingHalted;         // True if risk engine says stop trading
    private boolean goodTradingTime;       // True if within allowed time window
}
