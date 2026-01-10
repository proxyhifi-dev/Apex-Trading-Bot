package com.apex.backend.exception;

public class FyersApiException extends RuntimeException {
    private final int statusCode;

    public FyersApiException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public FyersApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public FyersApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
