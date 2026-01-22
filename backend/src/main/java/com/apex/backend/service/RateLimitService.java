package com.apex.backend.service;

import com.apex.backend.config.RateLimitProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final RateLimiterConfig scannerConfig;
    private final RateLimiterConfig tradeConfig;
    private final Map<String, RateLimiter> scannerLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> tradeLimiters = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties) {
        this.scannerConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getScanner().getLimitPerMinute())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(properties.getScanner().getTimeoutMs()))
                .build();
        this.tradeConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getTrade().getLimitPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(properties.getTrade().getTimeoutMs()))
                .build();
    }

    public boolean allowScanner(String key) {
        return scannerLimiters
                .computeIfAbsent(key, ignored -> RateLimiter.of("scanner-" + key, scannerConfig))
                .acquirePermission();
    }

    public boolean allowTrade(String key) {
        return tradeLimiters
                .computeIfAbsent(key, ignored -> RateLimiter.of("trade-" + key, tradeConfig))
                .acquirePermission();
    }
}
