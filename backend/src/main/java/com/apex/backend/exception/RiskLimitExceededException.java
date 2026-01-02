package com.apex.backend.exception;

public class RiskLimitExceededException extends RuntimeException {
    public RiskLimitExceededException(String message) {
        super(message);
    }
}
