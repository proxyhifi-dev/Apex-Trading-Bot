package com.apex.backend.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PaperAccountAmountRequest {
    private BigDecimal amount;
}
