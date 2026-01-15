package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TradingWindowService {

    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final StrategyProperties strategyProperties;

    public TradingWindowService(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public record WindowDecision(boolean allowed, String reason) {}

    public WindowDecision evaluate(Instant nowUtc) {
        StrategyProperties.TradingWindow config = strategyProperties.getTradingWindow();
        if (!config.isEnabled()) {
            return new WindowDecision(true, "Trading window disabled");
        }
        ZoneId zone = ZoneId.of(config.getTimezone());
        ZonedDateTime now = nowUtc.atZone(zone);
        LocalTime time = now.toLocalTime();

        if (isWithinAny(time, config.getBlackout())) {
            return new WindowDecision(false, "Blackout window");
        }
        if (!isWithinAny(time, config.getWindows())) {
            return new WindowDecision(false, "Outside trading window");
        }
        return new WindowDecision(true, "Within trading window");
    }

    private boolean isWithinAny(LocalTime time, List<String> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return false;
        }
        for (String window : ranges) {
            if (window == null || window.isBlank()) {
                continue;
            }
            String[] parts = window.split("-");
            if (parts.length != 2) {
                continue;
            }
            try {
                LocalTime start = LocalTime.parse(parts[0].trim(), WINDOW_FORMAT);
                LocalTime end = LocalTime.parse(parts[1].trim(), WINDOW_FORMAT);
                if (isWithinRange(time, start, end)) {
                    return true;
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // Ignore malformed windows
            }
        }
        return false;
    }

    private boolean isWithinRange(LocalTime time, LocalTime start, LocalTime end) {
        if (end.isAfter(start) || end.equals(start)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        return !time.isBefore(start) || !time.isAfter(end);
    }
}
