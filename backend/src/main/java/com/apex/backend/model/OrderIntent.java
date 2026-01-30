package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_intents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class OrderIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String clientOrderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    @Deprecated // Use orderState instead
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_state", nullable = false)
    @Builder.Default
    private OrderState orderState = OrderState.CREATED;

    private String brokerOrderId;

    private Integer filledQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "last_broker_status")
    private String lastBrokerStatus;  // Raw FYERS status string

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "correlation_id")
    private String correlationId;  // For audit trail

    @Column(name = "signal_id")
    private Long signalId;  // Link to StockScreeningResult

    /**
     * Transition to a new state with validation
     * @param newState Target state
     * @throws IllegalStateException if transition is invalid
     */
    public void transitionTo(OrderState newState) {
        if (!orderState.canTransitionTo(newState)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s for order %s", 
                    orderState, newState, clientOrderId)
            );
        }
        if (orderState == newState) {
            return;
        }
        OrderState previous = this.orderState;
        this.orderState = newState;
        this.status = newState.name(); // Keep legacy field in sync
        this.updatedAt = LocalDateTime.now();
        log.info("Order state transition requestId={} correlationId={} tradeId={} from={} to={}",
                clientOrderId,
                correlationId,
                MDC.get("tradeId"),
                previous,
                newState);
    }
}
