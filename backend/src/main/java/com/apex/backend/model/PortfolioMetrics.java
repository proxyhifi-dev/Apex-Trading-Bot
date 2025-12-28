package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
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
    private double totalEquity;
    private double availableBalance;
    private double dayPnl;
    private double monthPnl;
}
