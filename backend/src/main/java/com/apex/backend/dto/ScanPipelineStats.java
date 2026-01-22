package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanPipelineStats {
    private int trendPassed;
    private int volumePassed;
    private int breakoutPassed;
    private int rsiPassed;
    private int adxPassed;
    private int atrPassed;
    private int momentumPassed;
    private int squeezePassed;
    private int finalSignals;
}
