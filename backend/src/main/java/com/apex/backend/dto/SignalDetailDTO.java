package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDetailDTO {

    private Long id;
    private String symbol;
    private Integer signalScore;
    private String grade;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private LocalDateTime scanTime;
    private String approvalStatus;
    private String analysisReason;
    private String decisionReason;
    private String decisionNotes;
    private String decidedBy;
    private LocalDateTime decisionAt;
}
