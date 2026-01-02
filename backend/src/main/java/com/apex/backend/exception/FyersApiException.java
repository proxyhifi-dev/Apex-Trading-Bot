package com.apex.backend.exception;

public class FyersApiException extends RuntimeException {
    public FyersApiException(String message) {
        super(message);
    }
    
    public FyersApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
