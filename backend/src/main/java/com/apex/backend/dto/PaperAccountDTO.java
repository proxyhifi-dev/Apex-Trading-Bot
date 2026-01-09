package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaperAccountDTO {
    private Double startingCapital;
    private Double cashBalance;
    private Double reservedMargin;
    private Double realizedPnl;
    private Double unrealizedPnl;
    private LocalDateTime updatedAt;
}
