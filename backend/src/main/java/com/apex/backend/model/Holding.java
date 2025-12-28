package com.apex.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Holding {
    private String symbol;
    private int quantity;
    private double avgPrice;
    private double ltp;
    private double pnl;
}
