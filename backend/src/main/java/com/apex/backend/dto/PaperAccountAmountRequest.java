package com.apex.backend.dto;

import lombok.Data;

@Data
import java.math.BigDecimal;

public class PaperAccountAmountRequest {
    private BigDecimal amount;
}
