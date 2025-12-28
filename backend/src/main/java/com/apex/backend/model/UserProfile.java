// UserProfile.java
package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String name;
    private double availableFunds;
    private double totalInvested;
    private double currentValue;
    private double todaysPnl;
    private List<Object> holdings;
}
