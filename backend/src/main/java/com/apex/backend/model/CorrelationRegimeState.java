package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "correlation_regime_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationRegimeState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String regime;

    @Column(nullable = false)
    private Double avgOffDiagonalCorrelation;

    @Column(nullable = false)
    private Double sizingMultiplier;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
