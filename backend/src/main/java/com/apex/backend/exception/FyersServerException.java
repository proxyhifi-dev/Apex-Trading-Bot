package com.apex.backend.exception;

public class FyersServerException extends FyersApiException {
    public FyersServerException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
