package com.apex.backend.model;

/**
 * Signal lifecycle state machine
 * Tracks signal from creation to execution/rejection
 */
public enum SignalState {
    NEW,        // Signal created
    APPROVED,   // Risk checks passed, ready for execution
    REJECTED,   // Risk rejected
    EXPIRED;    // Signal expired (time-based)
    
    public boolean isTerminal() {
        return this == REJECTED || this == EXPIRED;
    }
}
