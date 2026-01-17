package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${apex.ws.broadcastTopics:false}")
    private boolean broadcastTopics;

    /**
     * Sends a log message to the frontend logs widget.
     * @param level INFO, WARN, ERROR, TRADE
     * @param message The text to display
     */
    public void broadcastLog(String level, String message) {
        Map<String, String> logEntry = new HashMap<>();
        logEntry.put("timestamp", LocalDateTime.now().format(formatter));
        logEntry.put("level", level);
        logEntry.put("message", message);

        // Push to the WebSocket topic the frontend is listening to
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/logs", logEntry);
        }
    }

    public void broadcastLog(Long userId, String level, String message) {
        Map<String, String> logEntry = new HashMap<>();
        logEntry.put("timestamp", LocalDateTime.now().format(formatter));
        logEntry.put("level", level);
        logEntry.put("message", message);
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/logs", logEntry);
        }
        if (broadcastTopics) {
            messagingTemplate.convertAndSend("/topic/logs", logEntry);
        }
    }

    // Convenience methods
    public void info(String message) { broadcastLog("INFO", message); }
    public void warn(String message) { broadcastLog("WARN", message); }
    public void error(String message) { broadcastLog("ERROR", message); }
    public void trade(String message) { broadcastLog("TRADE", message); }
}
