package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerRunStatusResponse {
    private Long runId;
    private String status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private ScanDiagnosticsBreakdown diagnostics;
}
