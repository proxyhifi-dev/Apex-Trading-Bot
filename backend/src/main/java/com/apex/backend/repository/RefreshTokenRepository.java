package com.apex.backend.repository;

import com.apex.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserId(Long userId);
    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, Instant now);
}
