package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_regime_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketRegimeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketRegime regime;

    @Column(nullable = false)
    private double adx;

    @Column(nullable = false)
    private double atrPercent;

    @Column(nullable = false)
    private LocalDateTime detectedAt;
}
