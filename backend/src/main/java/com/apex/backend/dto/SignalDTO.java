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
public class SignalDTO {
    private Long id;
    private String symbol;
    private Integer signalScore;
    private Double entryPrice;
    private LocalDateTime scanTime;
    private Boolean hasEntrySignal;
}
