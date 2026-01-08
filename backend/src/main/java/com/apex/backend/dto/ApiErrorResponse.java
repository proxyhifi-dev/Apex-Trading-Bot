package com.apex.backend.dto;

import lombok.Data;

@Data
public class ApiErrorResponse {
    private final String error;
    private final String details;
    private final long timestamp = System.currentTimeMillis();
}
