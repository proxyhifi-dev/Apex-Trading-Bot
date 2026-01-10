package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "decision_audits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    private String timeframe;

    @Column(nullable = false)
    private String decisionType;

    @Column(nullable = false)
    private LocalDateTime decisionTime;

    @Column(columnDefinition = "TEXT")
    private String details;
}
