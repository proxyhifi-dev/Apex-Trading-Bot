package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    private TradeType tradeType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double entryPrice;

    private Double exitPrice;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    // ✅ Initial Stop Loss (Fixed at entry)
    private Double stopLoss;

    // ✅ Dynamic Stop Loss (Moves with Trailing)
    private Double currentStopLoss;

    // ✅ Stored ATR for dynamic Targets (3xATR)
    private Double atr;

    // ✅ Track Highest Price for Trailing Logic
    private Double highestPrice;

    @Column(nullable = false)
    private boolean isPaperTrade;

    @Enumerated(EnumType.STRING)
    private TradeStatus status;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    private boolean breakevenMoved;

    private Double realizedPnl;

    public enum TradeType { LONG, SHORT }
    public enum TradeStatus { OPEN, CLOSED }
    public enum ExitReason { STOP_LOSS, TARGET, TIME_EXIT, MANUAL, SIGNAL }
}