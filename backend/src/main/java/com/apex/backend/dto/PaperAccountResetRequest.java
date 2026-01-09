package com.apex.backend.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PaperAccountResetRequest {
    private BigDecimal startingCapital;
}
