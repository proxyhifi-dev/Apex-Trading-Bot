package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "instruments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(name = "trading_symbol", nullable = false, unique = true)
    private String tradingSymbol;

    private String name;
    private String exchange;
    private String segment;

    @Column(name = "tick_size")
    private BigDecimal tickSize;

    @Column(name = "lot_size")
    private Integer lotSize;

    private String isin;
}
