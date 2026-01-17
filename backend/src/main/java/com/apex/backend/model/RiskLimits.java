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
@Table(name = "risk_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskLimits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(precision = 19, scale = 4)
    private BigDecimal dailyLossLimit;

    private Integer maxPositions;

    private Integer maxConsecutiveLosses;

    @Column(precision = 19, scale = 4)
    private BigDecimal portfolioHeatLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxNotionalExposure;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxSymbolExposure;
}
