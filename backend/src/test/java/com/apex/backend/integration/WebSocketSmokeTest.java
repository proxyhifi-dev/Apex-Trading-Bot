package com.apex.backend.integration;

import com.apex.backend.service.LogBroadcastService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LogBroadcastService logBroadcastService;

    @Test
    void websocketBroadcastsLogMessages() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Map<String, String>> payloadFuture = new CompletableFuture<>();
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {}
        ).get(2, TimeUnit.SECONDS);

        session.subscribe("/topic/logs", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                payloadFuture.complete((Map<String, String>) payload);
            }
        });

        logBroadcastService.info("smoke-test");

        Map<String, String> payload = payloadFuture.get(3, TimeUnit.SECONDS);
        assertThat(payload.get("message")).isEqualTo("smoke-test");
    }
}
