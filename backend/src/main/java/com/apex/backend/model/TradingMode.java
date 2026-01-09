package com.apex.backend.model;

public enum TradingMode {
    PAPER,
    LIVE;

    public static TradingMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return PAPER;
        }
        try {
            return TradingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PAPER;
        }
    }

    public static TradingMode fromRequest(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trading mode is required");
        }
        return TradingMode.valueOf(value.trim().toUpperCase());
    }
}
