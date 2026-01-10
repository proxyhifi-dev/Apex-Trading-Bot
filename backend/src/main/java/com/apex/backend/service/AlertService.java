package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final BroadcastService broadcastService;

    public void sendAlert(String type, String message) {
        broadcastService.broadcastBotStatus(Map.of(
                "alertType", type,
                "message", message,
                "timestamp", LocalDateTime.now()
        ));
    }
}
