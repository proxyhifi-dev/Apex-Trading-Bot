package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics {
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private double totalProfitLoss;
    private double averageWin;
    private double averageLoss;
    private double profitFactor;
    private double expectancy;
    private double maxDrawdown;
    private int longestWinStreak;
    private int longestLossStreak;
    private LocalDateTime lastTradeTime;
    private String lastTradeSymbol;
}
