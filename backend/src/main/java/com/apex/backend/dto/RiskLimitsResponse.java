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
public class RiskLimitsResponse {

    private BigDecimal dailyLossLimit;
    private Integer maxPositions;
    private Integer maxConsecutiveLosses;
    private BigDecimal portfolioHeatLimit;
    private BigDecimal maxNotionalExposure;
    private BigDecimal maxSymbolExposure;
}
