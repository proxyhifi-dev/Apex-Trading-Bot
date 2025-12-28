package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private TradingStrategy strategy;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType tradeType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double entryPrice;

    @Column
    private Double exitPrice;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column
    private LocalDateTime exitTime;

    @Column(nullable = false)
    private Double stopLoss;

    @Column
    private Double currentStopLoss;

    @Column
    private Double highestPrice;

    @Column
    private Integer barsInTrade;

    @Column(nullable = false)
    private boolean breakevenMoved;  // FIXED: primitive boolean

    @Column
    private Double pnl;

    @Column
    private Double pnlPercent;

    @Column(nullable = false)
    private boolean isPaperTrade;  // FIXED: primitive boolean

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private ExitReason exitReason;

    // Enums
    public enum TradeType {
        LONG, SHORT
    }

    public enum TradeStatus {
        OPEN, CLOSED, CANCELLED
    }

    public enum ExitReason {
        STOP_LOSS,
        TARGET,
        TRAILING_STOP,
        TIME_STOP,
        BREAKEVEN,
        MACD_EXIT,
        DIVERGENCE,
        MANUAL
    }
}
