package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskStatusDTO {
    // ✅ Matching fields for RiskController builder
    private boolean isTradingHalted;
    private boolean isGlobalHalt;
    private boolean isEntryHalt;
    private int consecutiveLosses;
    private double dailyDrawdownPct;
}