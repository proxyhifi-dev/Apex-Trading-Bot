package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaperAccountDTO {
    private BigDecimal startingCapital;
    private BigDecimal cashBalance;
    private BigDecimal reservedMargin;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private LocalDateTime updatedAt;
}
