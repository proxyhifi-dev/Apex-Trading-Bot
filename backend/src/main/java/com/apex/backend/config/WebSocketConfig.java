package com.apex.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityProperties securityProperties;
    private final Environment environment;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ Fix: Removed .withSockJS() to allow standard WebSocket clients (ws://)
        var endpoint = registry.addEndpoint("/ws");
        endpoint.setAllowedOrigins(resolveAllowedOrigins().toArray(new String[0]));
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private java.util.List<String> resolveAllowedOrigins() {
        java.util.List<String> configured = securityProperties.getWebsocket().getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            configured = securityProperties.getCors().getAllowedOrigins();
        }
        if (configured == null || configured.isEmpty()) {
            if (isProd()) {
                return java.util.List.of();
            }
            return java.util.List.of("http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:3000");
        }
        return configured;
    }
}
