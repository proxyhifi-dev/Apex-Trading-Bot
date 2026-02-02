package com.apex.backend.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM token encryption helper.
 *
 * Expected by other classes in this repo:
 * - TokenEncryptionConverter calls encrypt(...) and decrypt(...)
 * - TokenReEncryptionService calls looksEncrypted(...)
 *
 * Key source:
 * - ENV: SECURITY_TOKEN_ENCRYPTION_KEY (Base64, 32 bytes)
 * - or property: security.token-encryption-key
 *
 * Encrypted format:
 * - "enc:v1:" + Base64(iv + ciphertext)
 */
@Service
@Slf4j
public class TokenEncryptionService {

    private static final String PREFIX = "enc:v1:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private final Environment environment;
    private final ObjectProvider<com.apex.backend.service.SystemGuardService> systemGuardService;
    private final ObjectProvider<com.apex.backend.service.AuditEventService> auditEventService;

    private volatile SecretKey secretKey;

    public TokenEncryptionService(Environment environment,
                                  ObjectProvider<com.apex.backend.service.SystemGuardService> systemGuardService,
                                  ObjectProvider<com.apex.backend.service.AuditEventService> auditEventService) {
        this.environment = environment;
        this.systemGuardService = systemGuardService;
        this.auditEventService = auditEventService;
    }

    @PostConstruct
    public void init() {
        String keyB64 = firstNonBlank(
                environment.getProperty("SECURITY_TOKEN_ENCRYPTION_KEY"),
                environment.getProperty("security.token-encryption-key")
        );

        boolean isProd = isProdProfileActive();

        if (isBlank(keyB64)) {
            if (isProd) {
                String reason = "SECURITY_TOKEN_ENCRYPTION_KEY missing";
                log.error(reason);
                markSafeMode(reason, "TOKEN_ENCRYPTION_KEY_MISSING",
                        "Token encryption key missing in production", null);
            }
            this.secretKey = null; // encryption disabled in dev if key missing
            return;
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyB64.trim());
        } catch (IllegalArgumentException e) {
            String reason = "SECURITY_TOKEN_ENCRYPTION_KEY invalid Base64";
            log.error(reason, e);
            markSafeMode(reason, "TOKEN_ENCRYPTION_KEY_INVALID",
                    "Token encryption key invalid Base64", null);
            this.secretKey = null;
            return;
        }

        if (keyBytes.length != 32) {
            String reason = "SECURITY_TOKEN_ENCRYPTION_KEY invalid length";
            log.error("{} length={}", reason, keyBytes.length);
            markSafeMode(reason, "TOKEN_ENCRYPTION_KEY_INVALID",
                    "Token encryption key invalid length", java.util.Map.of("length", keyBytes.length));
            this.secretKey = null;
            return;
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Repo-compat: TokenEncryptionConverter expects this to exist and be callable.
     * Encrypts plain text and returns PREFIX + Base64(iv+ciphertext).
     *
     * If key is missing (dev), returns input as-is to avoid breaking local usage.
     * If already encrypted, returns as-is.
     */
    public String encrypt(String plain) {
        if (isBlank(plain)) return plain;
        if (looksEncrypted(plain)) return plain;
        if (secretKey == null) return plain;

        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token.", e);
        }
    }

    /**
     * Repo-compat: TokenEncryptionConverter expects this to exist and be callable.
     * Decrypts only if value is encrypted (enc:v1:...).
     *
     * If value is NOT encrypted, returns as-is.
     * If value IS encrypted but key is missing => throw (cannot decrypt).
     */
    public String decrypt(String value) {
        if (isBlank(value)) return value;
        if (!looksEncrypted(value)) return value;

        if (secretKey == null) {
            String reason = "Encrypted token present but SECURITY_TOKEN_ENCRYPTION_KEY is not configured";
            log.error(reason);
            markSafeMode(reason, "TOKEN_ENCRYPTION_KEY_MISSING",
                    "Encrypted token present but key missing", null);
            return null;
        }

        try {
            String b64 = value.substring(PREFIX.length());
            byte[] payload = Base64.getDecoder().decode(b64);

            if (payload.length < GCM_IV_BYTES + 16) {
                throw new IllegalArgumentException("Encrypted payload too short.");
            }

            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[payload.length - GCM_IV_BYTES];

            System.arraycopy(payload, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(payload, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            String reason = "Failed to decrypt token. Check SECURITY_TOKEN_ENCRYPTION_KEY.";
            log.error(reason, e);
            markSafeMode(reason, "TOKEN_DECRYPT_FAILURE",
                    "Failed to decrypt token", null);
            return null;
        }
    }

    /**
     * Repo-compat: TokenReEncryptionService calls looksEncrypted(...).
     */
    public boolean looksEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Optional helper (safe string literal).
     */
    public boolean looksLikeJson(String value) {
        if (value == null) return false;
        String v = value.trim();
        if ((v.startsWith("{") && v.endsWith("}")) || (v.startsWith("[") && v.endsWith("]"))) return true;
        return v.contains("\":");
    }

    // -------------------- helpers --------------------

    private boolean isProdProfileActive() {
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a;
        if (!isBlank(b)) return b;
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void markSafeMode(String reason, String action, String description, Object metadata) {
        try {
            com.apex.backend.service.SystemGuardService guardService = systemGuardService.getIfAvailable();
            if (guardService != null) {
                guardService.setSafeMode(true, reason, java.time.Instant.now());
            }
            com.apex.backend.service.AuditEventService auditService = auditEventService.getIfAvailable();
            if (auditService != null) {
                auditService.recordEvent(0L, "security", action, description, metadata);
            }
        } catch (Exception e) {
            log.warn("Failed to update safe mode for token encryption issue: {}", e.getMessage());
        }
    }
}
