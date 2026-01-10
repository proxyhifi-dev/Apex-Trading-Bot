package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MetricsService metricsService;

    /**
     * Pushes live position updates to the UI
     */
    public void broadcastPositions(Object positions) {
        messagingTemplate.convertAndSend("/topic/positions", positions);
        metricsService.incrementWebsocketPublishes();
    }

    /**
     * Pushes order updates
     */
    public void broadcastOrders(Object orders) {
        messagingTemplate.convertAndSend("/topic/orders", orders);
        metricsService.incrementWebsocketPublishes();
    }

    /**
     * Pushes P&L and Bot Status updates to the dashboard
     */
    public void broadcastSummary(Object summary) {
        messagingTemplate.convertAndSend("/topic/summary", summary);
        metricsService.incrementWebsocketPublishes();
    }

    /**
     * Pushes bot status updates
     */
    public void broadcastBotStatus(Object status) {
        messagingTemplate.convertAndSend("/topic/bot-status", status);
        metricsService.incrementWebsocketPublishes();
    }

    /**
     * Pushes new signals directly to the "Scanner Output" widget
     */
    public void broadcastSignal(Object signal) {
        messagingTemplate.convertAndSend("/topic/signals", signal);
        metricsService.incrementWebsocketPublishes();
    }
}
