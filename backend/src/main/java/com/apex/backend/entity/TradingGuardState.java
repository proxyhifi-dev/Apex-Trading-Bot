package com.apex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trading_guard_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingGuardState {

    @Id
    private Long userId;

    @Column(nullable = false)
    private int consecutiveLosses;

    private Instant lastLossAt;

    private Instant cooldownUntil;

    private LocalDate tradingDayDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayPnl;

    private Instant updatedAt;
}
