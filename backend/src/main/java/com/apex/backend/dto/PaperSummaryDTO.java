package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaperSummaryDTO {
    private BigDecimal cash;
    private BigDecimal used;
    private BigDecimal free;
    private BigDecimal pnl;
    private int positions;
}
