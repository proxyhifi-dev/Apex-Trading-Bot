// UserProfile.java
package com.apex.backend.model;

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
public class UserProfile {
    private String name;
    private String clientId;
    private String brokerStatus;
    private String broker;
    private String email;
    private String mobileNumber;
    private String statusMessage;
    private BigDecimal availableFunds;
    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal todaysPnl;
    private List<Object> holdings;
}
