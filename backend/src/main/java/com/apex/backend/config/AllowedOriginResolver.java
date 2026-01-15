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
        List<String> configured = securityProperties.getCors().getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            return isProd() ? List.of() : DEFAULT_DEV_ORIGINS;
        }
        return configured;
    }

    public List<String> resolveWebsocketAllowedOrigins() {
        List<String> configured = securityProperties.getWebsocket().getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            configured = securityProperties.getCors().getAllowedOrigins();
        }
        if (configured == null || configured.isEmpty()) {
            return isProd() ? List.of() : DEFAULT_DEV_ORIGINS;
        }
        return configured;
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
