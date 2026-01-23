package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanDiagnosticsBreakdown {
    private int totalSymbols;
    private int passedStage1;
    private int passedStage2;
    private int finalSignals;
    @Builder.Default
    private Map<String, Long> rejectedStage1ReasonCounts = Map.of();
    @Builder.Default
    private Map<String, Long> rejectedStage2ReasonCounts = Map.of();
}
