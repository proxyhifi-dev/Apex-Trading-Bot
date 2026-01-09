package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperPositionDTO {
    private String symbol;
    private Integer quantity;
    private BigDecimal entryPrice; // ✅ Renamed from avgPrice to match Controller
    private BigDecimal ltp;        // ✅ Added
    private BigDecimal pnl;        // ✅ Added
    private BigDecimal pnlPercent; // ✅ Added
}
