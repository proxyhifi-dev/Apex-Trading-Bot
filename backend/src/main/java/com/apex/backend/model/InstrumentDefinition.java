package com.apex.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentDefinition {

    private String symbol;
    private String name;
    private String exchange;
    private String segment;
    private BigDecimal tickSize;
    private Integer lotSize;
    private String isin;
}
