package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private TradeType tradeType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal exitPrice;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    // ✅ Initial Stop Loss (Fixed at entry)
    @Column(precision = 19, scale = 4)
    private BigDecimal stopLoss;

    // ✅ Dynamic Stop Loss (Moves with Trailing)
    @Column(precision = 19, scale = 4)
    private BigDecimal currentStopLoss;

    // ✅ Stored ATR for dynamic Targets (3xATR)
    @Column(precision = 19, scale = 4)
    private BigDecimal atr;

    // ✅ Track Highest Price for Trailing Logic
    @Column(precision = 19, scale = 4)
    private BigDecimal highestPrice;

    @Column(nullable = false)
    private boolean isPaperTrade;

    @Enumerated(EnumType.STRING)
    private TradeStatus status;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    private String exitReasonDetail;

    private boolean breakevenMoved;

    @Column(precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    @Column(precision = 19, scale = 4)
    private BigDecimal initialRiskAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal currentR;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxFavorableR;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxAdverseR;

    public enum TradeType { LONG, SHORT }
    public enum TradeStatus { OPEN, CLOSED }
    public enum ExitReason { STOP_LOSS, TARGET, TIME_EXIT, MANUAL, SIGNAL }
}
