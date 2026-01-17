package com.apex.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskLimitsUpdateRequest {

    @DecimalMin("0.0")
    private BigDecimal dailyLossLimit;

    @Positive
    private Integer maxPositions;

    @Positive
    private Integer maxConsecutiveLosses;

    @DecimalMin("0.0")
    private BigDecimal portfolioHeatLimit;

    @DecimalMin("0.0")
    private BigDecimal maxNotionalExposure;

    @DecimalMin("0.0")
    private BigDecimal maxSymbolExposure;
}
