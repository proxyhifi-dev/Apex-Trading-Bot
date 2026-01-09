package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_metrics")
public class PortfolioMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private TradingStrategy strategy;

    private LocalDateTime timestamp;
    @Column(precision = 19, scale = 4)
    private BigDecimal totalEquity;

    @Column(precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayPnl;

    @Column(precision = 19, scale = 4)
    private BigDecimal monthPnl;
}
