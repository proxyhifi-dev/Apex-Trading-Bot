package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperPositionDTO {
    private String symbol;
    private Integer quantity;
    private Double entryPrice; // ✅ Renamed from avgPrice to match Controller
    private Double ltp;        // ✅ Added
    private Double pnl;        // ✅ Added
    private Double pnlPercent; // ✅ Added
}