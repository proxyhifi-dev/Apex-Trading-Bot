package com.apex.backend.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AllowedOriginResolver {

    private static final List<String> DEFAULT_DEV_ORIGINS = List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:3000"
    );

    private final SecurityProperties securityProperties;
    private final Environment environment;

    public AllowedOriginResolver(SecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    public List<String> resolveCorsAllowedOrigins() {
        List<String> envOrigins = parseOrigins(environment.getProperty("APEX_ALLOWED_ORIGINS"));
        List<String> configured = parseOrigins(securityProperties.getCors().getAllowedOrigins());
        if (isProd()) {
            if (!envOrigins.isEmpty()) {
                return envOrigins;
            }
            if (!configured.isEmpty()) {
                return configured;
            }
            throw new IllegalStateException("CORS allowed origins must be configured for production via " +
                    "APEX_ALLOWED_ORIGINS or apex.security.cors.allowed-origins");
        }
        if (!envOrigins.isEmpty()) {
            return envOrigins;
        }
        return configured.isEmpty() ? DEFAULT_DEV_ORIGINS : configured;
    }

    public List<String> resolveWebsocketAllowedOrigins() {
        List<String> envOrigins = parseOrigins(environment.getProperty("APEX_ALLOWED_ORIGINS"));
        List<String> configured = parseOrigins(securityProperties.getWebsocket().getAllowedOrigins());
        if (isProd()) {
            if (!envOrigins.isEmpty()) {
                return envOrigins;
            }
            if (configured.isEmpty()) {
                configured = parseOrigins(securityProperties.getCors().getAllowedOrigins());
            }
            if (!configured.isEmpty()) {
                return configured;
            }
            throw new IllegalStateException("WebSocket allowed origins must be configured for production via " +
                    "APEX_ALLOWED_ORIGINS or apex.security.websocket.allowed-origins");
        }
        if (!envOrigins.isEmpty()) {
            return envOrigins;
        }
        if (configured.isEmpty()) {
            configured = parseOrigins(securityProperties.getCors().getAllowedOrigins());
        }
        return configured.isEmpty() ? DEFAULT_DEV_ORIGINS : configured;
    }

    private List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> parseOrigins(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
