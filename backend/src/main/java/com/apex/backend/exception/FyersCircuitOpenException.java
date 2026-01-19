package com.apex.backend.exception;

public class FyersCircuitOpenException extends RuntimeException {
    public FyersCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
