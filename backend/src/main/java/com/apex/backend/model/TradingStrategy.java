package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

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
    private boolean active = true;
    private double initialCapital;
    private int minEntryScore;
    private double rsiNeutral;
}
