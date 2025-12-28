package com.apex.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {
    private double totalEquity;
    private double availableBalance;
    private double dayPnl;
    private double monthPnl;
    private List<String> holdings;
}
