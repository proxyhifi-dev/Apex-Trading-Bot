package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BrokerCircuitBreakerService {

    private final AdvancedTradingProperties advancedTradingProperties;

    private int failureCount = 0;
    private LocalDateTime openUntil;

    public boolean allowRequest() {
        if (openUntil == null) {
            return true;
        }
        if (LocalDateTime.now().isAfter(openUntil)) {
            openUntil = null;
            failureCount = 0;
            return true;
        }
        return false;
    }

    public void recordFailure() {
        failureCount++;
        if (failureCount >= advancedTradingProperties.getBroker().getFailureThreshold()) {
            openUntil = LocalDateTime.now().plusSeconds(advancedTradingProperties.getBroker().getCoolDownSeconds());
        }
    }

    public void recordSuccess() {
        failureCount = 0;
        openUntil = null;
    }

    public LocalDateTime getOpenUntil() {
        return openUntil;
    }
}
