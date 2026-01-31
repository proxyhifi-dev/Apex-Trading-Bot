package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trade_state_audits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeStateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionState fromState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionState toState;

    @Column(length = 100)
    private String reason;

    @Column
    private String detail;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(nullable = false)
    private Instant createdAt;
}
