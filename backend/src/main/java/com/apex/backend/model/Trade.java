package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type")
    private TradeType tradeType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 19, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "stop_loss", precision = 19, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "current_stop_loss", precision = 19, scale = 4)
    private BigDecimal currentStopLoss;

    @Column(name = "atr", precision = 19, scale = 4)
    private BigDecimal atr;

    @Column(name = "highest_price", precision = 19, scale = 4)
    private BigDecimal highestPrice;

    @Column(name = "is_paper_trade", nullable = false)
    private boolean isPaperTrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TradeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason")
    private ExitReason exitReason;

    @Column(name = "exit_reason_detail")
    private String exitReasonDetail;

    @Column(name = "breakeven_moved")
    private boolean breakevenMoved;

    @Column(name = "realized_pnl", precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    @Column(name = "initial_risk_amount", precision = 19, scale = 4)
    private BigDecimal initialRiskAmount;

    @Column(name = "current_r", precision = 19, scale = 4)
    private BigDecimal currentR;

    @Column(name = "max_favorable_r", precision = 19, scale = 4)
    private BigDecimal maxFavorableR;

    @Column(name = "max_adverse_r", precision = 19, scale = 4)
    private BigDecimal maxAdverseR;

    @Column(name = "sizing_multiplier", precision = 10, scale = 4)
    private BigDecimal sizingMultiplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_state", nullable = false)
    @Builder.Default
    private PositionState positionState = PositionState.OPENING;

    @Column(name = "stop_order_id")
    private String stopOrderId;  // Broker orderId for stop-loss

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_order_state")
    private OrderState stopOrderState;  // State of stop-loss order

    @Column(name = "stop_acked_at")
    private LocalDateTime stopAckedAt;

    /**
     * Transition to a new position state with validation
     * @param newState Target state
     * @throws IllegalStateException if transition is invalid
     */
    public void transitionTo(PositionState newState) {
        if (!positionState.canTransitionTo(newState)) {
            throw new IllegalStateException(
                String.format("Invalid position state transition: %s -> %s for trade %d", 
                    positionState, newState, id)
            );
        }
        if (positionState == newState) {
            return;
        }
        PositionState previous = this.positionState;
        this.positionState = newState;
        log.info("Trade state transition tradeId={} requestId={} correlationId={} from={} to={}",
                id,
                MDC.get("requestId"),
                MDC.get("correlationId"),
                previous,
                newState);
    }

    public enum TradeType { LONG, SHORT }
    public enum TradeStatus { OPEN, CLOSED }
    public enum ExitReason { STOP_LOSS, TARGET, TIME_EXIT, MANUAL, SIGNAL }
}
