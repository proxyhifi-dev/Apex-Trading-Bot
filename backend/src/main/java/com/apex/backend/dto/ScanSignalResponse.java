package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanSignalResponse {
    private String symbol;
    private double score;
    private String grade;
    private double entryPrice;
    private LocalDateTime scanTime;
    private String reason;
}
