package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MetricsService metricsService;

    @Value("${apex.ws.broadcastTopics:false}")
    private boolean broadcastTopics;

    /**
     * Pushes live position updates to the UI
     */
    public void broadcastPositions(Object positions) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/positions", positions);
            metricsService.incrementWebsocketPublishes();
        }
    }

    public void broadcastPositions(Long userId, Object positions) {
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/positions", positions);
            metricsService.incrementWebsocketPublishes();
        }
        broadcastPositions(positions);
    }

    /**
     * Pushes order updates
     */
    public void broadcastOrders(Object orders) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/orders", orders);
            metricsService.incrementWebsocketPublishes();
        }
    }

    public void broadcastOrders(Long userId, Object orders) {
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/orders", orders);
            metricsService.incrementWebsocketPublishes();
        }
        broadcastOrders(orders);
    }

    /**
     * Pushes P&L and Bot Status updates to the dashboard
     */
    public void broadcastSummary(Object summary) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/summary", summary);
            metricsService.incrementWebsocketPublishes();
        }
    }

    public void broadcastSummary(Long userId, Object summary) {
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/summary", summary);
            metricsService.incrementWebsocketPublishes();
        }
        broadcastSummary(summary);
    }

    /**
     * Pushes bot status updates
     */
    public void broadcastBotStatus(Object status) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/bot-status", status);
            metricsService.incrementWebsocketPublishes();
        }
    }

    public void broadcastBotStatus(Long userId, Object status) {
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/bot-status", status);
            metricsService.incrementWebsocketPublishes();
        }
        broadcastBotStatus(status);
    }

    /**
     * Pushes reconciliation updates
     */
    public void broadcastReconcile(Object report) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/reconcile", report);
            metricsService.incrementWebsocketPublishes();
        }
    }

    /**
     * Pushes new signals directly to the "Scanner Output" widget
     */
    public void broadcastSignal(Object signal) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/signals", signal);
            metricsService.incrementWebsocketPublishes();
        }
    }

    public void broadcastSignal(Long userId, Object signal) {
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/signals", signal);
            metricsService.incrementWebsocketPublishes();
        }
        broadcastSignal(signal);
    }

    /**
     * Broadcasts risk rejection events with threshold and current values
     */
    public void broadcastReject(RejectEvent event) {
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/rejects", event);
            metricsService.incrementWebsocketPublishes();
        }
    }

    /**
     * Reject event with full details for UI display
     */
    public record RejectEvent(
            String reasonCode,
            Double threshold,
            Double currentValue,
            String symbol,
            Long signalId,
            String clientOrderId,
            LocalDateTime timestamp
    ) {
        public RejectEvent(String reasonCode, Double threshold, Double currentValue, 
                          String symbol, Long signalId, String clientOrderId) {
            this(reasonCode, threshold, currentValue, symbol, signalId, clientOrderId, LocalDateTime.now());
        }
    }
}
