package com.apex.backend.service;

import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.RefreshToken;
import com.apex.backend.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public RefreshToken storeToken(Long userId, String refreshToken) {
        Instant issuedAt = extractIssuedAt(refreshToken);
        Instant expiresAt = extractExpiresAt(refreshToken);
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(refreshToken))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return refreshTokenRepository.save(token);
    }

    public void rotateToken(Long userId, String oldToken, String newToken) {
        RefreshToken current = requireActiveToken(oldToken, userId);
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(hash(newToken));
        refreshTokenRepository.save(current);
        storeToken(userId, newToken);
    }

    public void revokeToken(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    public void revokeAll(Long userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, Instant.now());
        Instant now = Instant.now();
        tokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(tokens);
    }

    public void ensureActive(String refreshToken, Long userId) {
        requireActiveToken(refreshToken, userId);
    }

    private RefreshToken requireActiveToken(String refreshToken, Long userId) {
        String tokenHash = hash(refreshToken);
        Optional<RefreshToken> token = refreshTokenRepository.findByTokenHash(tokenHash);
        if (token.isEmpty()) {
            throw new UnauthorizedException("Refresh token revoked");
        }
        RefreshToken refresh = token.get();
        if (!refresh.getUserId().equals(userId)) {
            throw new UnauthorizedException("Refresh token user mismatch");
        }
        if (refresh.getRevokedAt() != null || refresh.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token revoked");
        }
        return refresh;
    }

    private Instant extractIssuedAt(String token) {
        Claims claims = parseClaims(token);
        return claims.getIssuedAt().toInstant();
    }

    private Instant extractExpiresAt(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}
