package com.apex.backend.model;

/**
 * Order lifecycle state machine
 * Enforces deterministic state transitions for order execution
 */
public enum OrderState {
    CREATED,           // OrderIntent created, not yet sent to broker
    SENT,              // HTTP request sent to FYERS
    ACKED,             // Broker acknowledged (FYERS returned orderId)
    PART_FILLED,       // Partial fill received
    FILLED,            // Fully filled
    CANCEL_REQUESTED,  // Cancel request sent
    CANCELLED,         // Broker confirmed cancellation
    REJECTED,          // Broker rejected
    EXPIRED,           // Timeout expired
    UNKNOWN;           // Broker status unknown/mismatch
    
    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }
    
    public boolean canTransitionTo(OrderState target) {
        if (target == null) return false;
        if (this == target) return true; // Self-transitions allowed for idempotency
        
        return switch (this) {
            case CREATED -> target == SENT || target == REJECTED;
            case SENT -> target == ACKED || target == REJECTED || target == UNKNOWN;
            case ACKED -> target == PART_FILLED || target == FILLED || target == CANCELLED || target == REJECTED || target == EXPIRED;
            case PART_FILLED -> target == FILLED || target == CANCEL_REQUESTED || target == CANCELLED || target == EXPIRED;
            case CANCEL_REQUESTED -> target == CANCELLED || target == FILLED || target == UNKNOWN;
            default -> false;
        };
    }
    
    public static OrderState fromString(String status) {
        if (status == null || status.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Map legacy string statuses
            return switch (status.toUpperCase()) {
                case "PENDING" -> CREATED;
                case "PLACED" -> SENT;
                case "PARTIAL", "PARTIALLY_FILLED" -> PART_FILLED;
                case "COMPLETE" -> FILLED;
                case "CANCELED" -> CANCELLED;
                default -> UNKNOWN;
            };
        }
    }
}
