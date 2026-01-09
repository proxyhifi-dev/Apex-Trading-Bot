// UserProfile.java
package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String name;
    private BigDecimal availableFunds;
    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal todaysPnl;
    private List<Object> holdings;
}
