package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trading_strategies")
public class TradingStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Core indicator parameters
    private int rsiPeriod;
    private int macdFastPeriod;
    private int macdSlowPeriod;
    private int macdSignalPeriod;
    private int adxPeriod;
    private int bollingerPeriod;
    private double bollingerStdDev;

    // Weights for scoring
    private int rsiWeight;
    private int macdWeight;
    private int adxWeight;
    private int squeezeWeight;

    // Extra fields
        @Builder.Default
    private boolean active = true;
    @Column(precision = 19, scale = 4)
    private BigDecimal initialCapital;
    private int minEntryScore;
    private double rsiNeutral;
}
