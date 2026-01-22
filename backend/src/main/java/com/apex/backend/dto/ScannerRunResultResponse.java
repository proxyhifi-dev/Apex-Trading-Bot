package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerRunResultResponse {
    private Long runId;
    private ScanDiagnosticsBreakdown diagnostics;
    private List<ScanSignalResponse> signals;
}
