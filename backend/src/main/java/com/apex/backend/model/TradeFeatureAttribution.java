package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_feature_attribution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeFeatureAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private Double normalizedValue;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private Double contribution;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
