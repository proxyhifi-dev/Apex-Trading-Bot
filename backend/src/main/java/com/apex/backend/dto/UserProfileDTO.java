package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String name;

    // Legacy support (points to Real funds usually)
    private Double availableFunds;

    // ✅ NEW FIELDS REQUIRED BY PORTFOLIO SERVICE
    private Double availableRealFunds;
    private Double availablePaperFunds;

    private Double totalInvested;
    private Double currentValue;
    private Double todaysPnl;
    private List<HoldingDTO> holdings;
}