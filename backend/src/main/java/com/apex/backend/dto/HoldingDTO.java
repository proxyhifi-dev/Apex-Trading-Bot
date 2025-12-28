package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingDTO {
    private String symbol;
    private Integer quantity;
    private Double avgPrice;
    private Double currentPrice;
    private Double pnl;
    private Double pnlPercent;
}
