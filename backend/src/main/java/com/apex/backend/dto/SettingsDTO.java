package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SettingsDTO {
    private String mode;
    private Integer maxPositions;
    private RiskLimitsDTO riskLimits;

    @Data
    @Builder
    public static class RiskLimitsDTO {
        private Double maxRiskPerTradePercent;
        private Double maxDailyLossPercent;
    }
}
