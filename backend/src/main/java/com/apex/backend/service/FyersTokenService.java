package com.apex.backend.service;

import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FyersTokenService {

    private final UserRepository userRepository;
    private final FyersAuthService fyersAuthService;
    private final SystemGuardService systemGuardService;
    private final BrokerStatusService brokerStatusService;
    private final AuditEventService auditEventService;

    public String getAccessToken(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getFyersConnected()))
                .filter(user -> Boolean.TRUE.equals(user.getFyersTokenActive()))
                .map(user -> user.getFyersToken())
                .orElse(null);
    }

    @Transactional
    public Optional<String> refreshAccessToken(Long userId) {
        String refreshToken = userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getFyersConnected()))
                .filter(user -> Boolean.TRUE.equals(user.getFyersTokenActive()))
                .map(user -> user.getFyersRefreshToken())
                .orElse(null);
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        try {
            FyersAuthService.FyersTokens tokens = fyersAuthService.refreshAccessToken(refreshToken);
            userRepository.findById(userId).ifPresent(user -> {
                user.setFyersToken(tokens.accessToken());
                if (tokens.refreshToken() != null) {
                    user.setFyersRefreshToken(tokens.refreshToken());
                }
                user.setFyersConnected(true);
                user.setFyersTokenActive(true);
                userRepository.save(user);
            });
            return Optional.of(tokens.accessToken());
        } catch (Exception e) {
            log.warn("Failed to refresh FYERS token for user {}: {}", userId, e.getMessage());
            systemGuardService.setSafeMode(true, "FYERS_REFRESH_FAILED", java.time.Instant.now());
            brokerStatusService.markDegraded("FYERS", "TOKEN_REFRESH_FAILED");
            auditEventService.recordEvent(userId, "broker_auth", "FYERS_REFRESH_FAILED",
                    "FYERS refresh token failed", java.util.Map.of("error", e.getMessage()));
            return Optional.empty();
        }
    }
}
