package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class ScannerTimeframeMapper {

    public String toFyersTimeframe(String tf) {
        if (tf == null) {
            throw new BadRequestException("Timeframe is required");
        }
        String normalized = tf.trim().toLowerCase();
        return switch (normalized) {
            case "1m", "1" -> "1";
            case "3m", "3" -> "3";
            case "5m", "5" -> "5";
            case "10m", "10" -> "10";
            case "15m", "15" -> "15";
            case "30m", "30" -> "30";
            case "45m", "45" -> "45";
            case "1h", "60", "60m" -> "60";
            case "2h", "120", "120m" -> "120";
            case "4h", "240", "240m" -> "240";
            case "1d", "d" -> "D";
            case "1w", "w" -> "W";
            case "1mo", "mo" -> "M";
            default -> throw new BadRequestException("Unsupported timeframe: " + tf);
        };
    }
}
