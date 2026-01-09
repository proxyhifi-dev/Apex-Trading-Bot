package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SignalDTO {
    private Long id;
    private String symbol;
    private Integer signalScore;
    private String grade;
    private BigDecimal entryPrice;
    private LocalDateTime scanTime;
    private boolean hasEntrySignal; // ✅ Ensure this is present
}
