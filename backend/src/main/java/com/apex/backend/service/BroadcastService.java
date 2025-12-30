package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Pushes live position updates to the UI
     */
    public void broadcastPositions(Object positions) {
        messagingTemplate.convertAndSend("/topic/positions", positions);
    }

    /**
     * Pushes P&L and Bot Status updates to the dashboard
     */
    public void broadcastSummary(Object summary) {
        messagingTemplate.convertAndSend("/topic/summary", summary);
    }

    /**
     * Pushes new signals directly to the "Scanner Output" widget
     */
    public void broadcastSignal(Object signal) {
        messagingTemplate.convertAndSend("/topic/signals", signal);
    }
}