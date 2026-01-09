package com.apex.backend.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class PaperAccountResetRequest {
    @JsonAlias("startingCapital")
    private BigDecimal balance;
}
