package com.apex.backend.exception;

public class FyersRateLimitException extends RuntimeException {
    public FyersRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
