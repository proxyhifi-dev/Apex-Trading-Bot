package com.apex.backend.service;

import com.apex.backend.dto.BrokerStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BrokerConnectionService {

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;

    public BrokerStatusResponse getStatus(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            return BrokerStatusResponse.builder()
                    .connected(false)
                    .broker("FYERS")
                    .lastCheckedAt(now)
                    .error("Broker token missing")
                    .build();
        }
        try {
            Map<String, Object> profile = fyersService.getProfileForUser(userId);
            String accountId = extractProfileId(profile);
            return BrokerStatusResponse.builder()
                    .connected(true)
                    .broker("FYERS")
                    .lastCheckedAt(now)
                    .accountId(accountId)
                    .build();
        } catch (Exception e) {
            log.warn("Broker ping failed for user {}", userId, e);
            return BrokerStatusResponse.builder()
                    .connected(false)
                    .broker("FYERS")
                    .lastCheckedAt(now)
                    .error(e.getMessage())
                    .build();
        }
    }

    private String extractProfileId(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object fyId = dataMap.get("fy_id");
            return fyId != null ? fyId.toString() : null;
        }
        return null;
    }
}
