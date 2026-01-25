package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {

    private String requestId;
    private Instant startedAt;
    private long durationMs;
    private int symbolsScanned;
    private ScanPipelineStats pipeline;
    @Builder.Default
    private ScanDiagnosticsBreakdown diagnostics = ScanDiagnosticsBreakdown.builder().build();
    private List<ScanRejectReasonCount> rejectReasonsTop;
    private List<ScanSignalResponse> signals;
    private List<ScanError> errors;
}
