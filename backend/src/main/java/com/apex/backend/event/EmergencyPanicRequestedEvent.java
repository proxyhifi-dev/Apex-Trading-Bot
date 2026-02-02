package com.apex.backend.event;

import java.time.Instant;
import java.util.Map;

public record EmergencyPanicRequestedEvent(
        Long userId,
        String reason,
        String source,
        Instant createdAt,
        Map<String, Object> metadata
) {
}
