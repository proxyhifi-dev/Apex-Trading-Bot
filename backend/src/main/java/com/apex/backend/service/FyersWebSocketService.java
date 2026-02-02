package com.apex.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FyersWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuditEventService auditEventService;
    private final LogBroadcastService logBroadcastService;

    @Value("${fyers.ws.enabled:false}")
    private boolean enabled;

    @Value("${fyers.ws.market-data-url:}")
    private String marketDataUrl;

    @Value("${fyers.ws.order-updates-url:}")
    private String orderUpdatesUrl;

    @Value("${fyers.ws.access-token:}")
    private String accessToken;

    @Value("${fyers.api.app-id:}")
    private String appId;

    @Value("${fyers.ws.subscribe.market-data-payload:}")
    private String marketDataSubscribePayload;

    @Value("${fyers.ws.subscribe.order-updates-payload:}")
    private String orderUpdatesSubscribePayload;

    @Value("${fyers.ws.reconnect-backoff-ms:5000}")
    private long reconnectBackoffMs;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fyers-ws-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    private OkHttpClient webSocketClient;
    private WebSocket marketSocket;
    private WebSocket orderSocket;

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("FYERS WebSocket disabled via config");
            return;
        }
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("FYERS WebSocket enabled but no access token configured");
            auditEventService.recordEvent(null, "BROKER", "FYERS_WS_DISABLED",
                    "FYERS WebSocket enabled but access token is missing", Map.of("timestamp", Instant.now().toString()));
            return;
        }
        if (appId == null || appId.isBlank()) {
            log.warn("FYERS WebSocket enabled but app-id missing");
            auditEventService.recordEvent(null, "BROKER", "FYERS_WS_DISABLED",
                    "FYERS WebSocket enabled but app-id is missing", Map.of("timestamp", Instant.now().toString()));
            return;
        }
        webSocketClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        connectMarketData();
        connectOrderUpdates();
    }

    private void connectMarketData() {
        if (marketDataUrl == null || marketDataUrl.isBlank()) {
            return;
        }
        marketSocket = openSocket(marketDataUrl, "/topic/fyers/market-data", marketDataSubscribePayload);
    }

    private void connectOrderUpdates() {
        if (orderUpdatesUrl == null || orderUpdatesUrl.isBlank()) {
            return;
        }
        orderSocket = openSocket(orderUpdatesUrl, "/topic/fyers/order-updates", orderUpdatesSubscribePayload);
    }

    private WebSocket openSocket(String url, String topic, String subscribePayload) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", appId + ":" + accessToken)
                .build();
        return webSocketClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("FYERS WebSocket connected topic={} url={}", topic, url);
                if (subscribePayload != null && !subscribePayload.isBlank()) {
                    webSocket.send(subscribePayload);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                messagingTemplate.convertAndSend(topic, Map.of(
                        "payload", text,
                        "timestamp", Instant.now().toString()
                ));
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.warn("FYERS WebSocket closed topic={} code={} reason={}", topic, code, reason);
                scheduleReconnect(url, topic, subscribePayload);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.warn("FYERS WebSocket failure topic={} message={}", topic, t.getMessage());
                logBroadcastService.error("FYERS WebSocket failure: " + t.getMessage());
                auditEventService.recordEvent(null, "BROKER", "FYERS_WS_ERROR",
                        "FYERS WebSocket failure", Map.of(
                                "topic", topic,
                                "url", url,
                                "message", t.getMessage(),
                                "timestamp", Instant.now().toString()
                        ));
                scheduleReconnect(url, topic, subscribePayload);
            }
        });
    }

    private void scheduleReconnect(String url, String topic, String subscribePayload) {
        if (!enabled || webSocketClient == null) {
            return;
        }
        scheduler.schedule(() -> {
            if (Objects.equals(topic, "/topic/fyers/market-data")) {
                marketSocket = openSocket(url, topic, subscribePayload);
            } else {
                orderSocket = openSocket(url, topic, subscribePayload);
            }
        }, Math.max(1000, reconnectBackoffMs), TimeUnit.MILLISECONDS);
    }
}
