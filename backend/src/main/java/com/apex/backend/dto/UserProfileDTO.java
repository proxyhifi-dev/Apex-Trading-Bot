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
    private Double availableFunds;
    private Double totalInvested;
    private Double currentValue;
    private Double todaysPnl;
    private List<HoldingDTO> holdings;
}
