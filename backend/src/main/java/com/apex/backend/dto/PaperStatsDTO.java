package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperStatsDTO {
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private Double winRate;
    private BigDecimal totalProfit;
    private BigDecimal totalLoss;
    private BigDecimal netPnl; // ✅ FIXED: Changed netPnL to netPnl to match Controller
    private BigDecimal profitFactor; // ✅ Added to prevent potential future error
}
