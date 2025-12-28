package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "risk_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate tradingDate;

    @Column(nullable = false)
    private Double dailyLoss;

    @Column(nullable = false)
    private Integer consecutiveLosses;

    @Column(nullable = false)
    private Double currentEquity;

    @Column
    private Integer totalTrades;

    @Column
    private Integer winningTrades;

    @Column
    private Integer losingTrades;

    @Column
    private Boolean tradingHalted;

    @Column
    private String haltReason;
}
