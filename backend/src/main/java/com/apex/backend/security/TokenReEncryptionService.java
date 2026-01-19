package com.apex.backend.security;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenReEncryptionService {

    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @EventListener(ApplicationReadyEvent.class)
    public void reencryptLegacyTokens() {
        for (User user : userRepository.findAll()) {
            boolean updated = false;
            String token = user.getFyersToken();
            if (token != null && !token.isBlank() && !tokenEncryptionService.looksEncrypted(token)) {
                user.setFyersToken(token);
                updated = true;
            }
            String refreshToken = user.getFyersRefreshToken();
            if (refreshToken != null && !refreshToken.isBlank() && !tokenEncryptionService.looksEncrypted(refreshToken)) {
                user.setFyersRefreshToken(refreshToken);
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
                log.info("Re-encrypted legacy broker tokens for user {}", user.getId());
            }
        }
    }
}
