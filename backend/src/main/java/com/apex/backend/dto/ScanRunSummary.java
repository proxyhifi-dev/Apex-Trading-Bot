package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRunSummary {
    private Long scanId;
    private Instant startedAt;
    private Instant endedAt;
    private int symbolsScanned;
    @Builder.Default
    private Map<String, Long> stagePassCounts = Map.of();
    private int signalsFound;
    private String status;
    @Builder.Default
    private List<String> errors = List.of();
}
