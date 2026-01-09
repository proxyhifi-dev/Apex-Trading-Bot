package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String name;

    // Legacy support (points to Real funds usually)
    private BigDecimal availableFunds;

    // ✅ NEW FIELDS REQUIRED BY PORTFOLIO SERVICE
    private BigDecimal availableRealFunds;
    private BigDecimal availablePaperFunds;

    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal todaysPnl;
    private List<HoldingDTO> holdings;
}
