package com.apex.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TradeCooldownService {

    private final Map<String, Instant> lastTradeBySymbol = new ConcurrentHashMap<>();

    @Value("${risk.trade-cooldown.minutes:5}")
    private long cooldownMinutes;

    public void recordTrade(String symbol, Long userId) {
        if (symbol == null || symbol.isBlank() || userId == null) {
            return;
        }
        lastTradeBySymbol.put(key(symbol, userId), Instant.now());
        log.debug("Recorded trade cooldown for symbol {} user {}", symbol, userId);
    }

    public boolean isInCooldown(String symbol, Long userId) {
        if (symbol == null || symbol.isBlank() || userId == null) {
            return false;
        }
        Instant lastTrade = lastTradeBySymbol.get(key(symbol, userId));
        if (lastTrade == null) {
            return false;
        }
        return Duration.between(lastTrade, Instant.now()).toMinutes() < cooldownMinutes;
    }

    public long getRemainingCooldown(String symbol, Long userId) {
        if (!isInCooldown(symbol, userId)) {
            return 0;
        }
        Instant lastTrade = lastTradeBySymbol.get(key(symbol, userId));
        long elapsedSeconds = Duration.between(lastTrade, Instant.now()).getSeconds();
        long totalSeconds = Duration.ofMinutes(cooldownMinutes).getSeconds();
        return Math.max(0, totalSeconds - elapsedSeconds);
    }

    private String key(String symbol, Long userId) {
        return userId + ":" + symbol.toUpperCase();
    }
}
