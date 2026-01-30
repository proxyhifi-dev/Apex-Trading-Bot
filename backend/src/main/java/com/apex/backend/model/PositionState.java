package com.apex.backend.model;

/**
 * Position lifecycle state machine
 * Tracks position from planning to closure
 */
public enum PositionState {
    OPENING,    // Entry order sent, awaiting fill
    OPEN,       // Position open, stop-loss must be ACKED
    CLOSING,    // Exit order sent
    CLOSED,     // Position closed
    ERROR;      // Error state (stop failed, mismatch, etc.)
    
    public boolean isTerminal() {
        return this == CLOSED || this == ERROR;
    }
    
    public boolean canTransitionTo(PositionState target) {
        if (target == null) return false;
        if (this == target) return true;
        
        return switch (this) {
            case OPENING -> target == OPEN || target == ERROR || target == CLOSED;
            case OPEN -> target == CLOSING || target == CLOSED || target == ERROR;
            case CLOSING -> target == CLOSED || target == ERROR;
            default -> false;
        };
    }
}
