package com.apex.backend.model;

/**
 * Position lifecycle state machine
 * Tracks position from planning to closure
 */
public enum PositionState {
    PLANNED,    // Trade planned, entry order not yet sent
    OPENING,    // Entry order sent, awaiting fill
    OPEN,       // Position open, stop-loss must be ACKED
    EXITING,    // Exit order sent
    CLOSED,     // Position closed
    ERROR;      // Error state (stop failed, mismatch, etc.)
    
    public boolean isTerminal() {
        return this == CLOSED || this == ERROR;
    }
    
    public boolean canTransitionTo(PositionState target) {
        if (target == null) return false;
        if (this == target) return true;
        
        return switch (this) {
            case PLANNED -> target == OPENING || target == ERROR;
            case OPENING -> target == OPEN || target == ERROR || target == CLOSED;
            case OPEN -> target == EXITING || target == CLOSED || target == ERROR;
            case EXITING -> target == CLOSED || target == ERROR;
            default -> false;
        };
    }
}
