package com.apex.backend.security;

import jakarta.annotation.PostConstruct;
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
 * - Uses env var: SECURITY_TOKEN_ENCRYPTION_KEY (Base64) OR spring property: security.token-encryption-key
 * - Prefixes encrypted values with: enc:v1:
 * - Safe for DB storage (Base64 payload)
 *
 * IMPORTANT:
 * - Provide 32 bytes key (AES-256) encoded as Base64.
 */
@Service
public class TokenEncryptionService {

    private static final String PREFIX = "enc:v1:";
    private static final int GCM_IV_BYTES = 12;          // recommended IV length for GCM
    private static final int GCM_TAG_BITS = 128;         // 16 bytes auth tag
    private static final SecureRandom RNG = new SecureRandom();

    private final Environment environment;

    // resolved at startup
    private volatile SecretKey secretKey;

    public TokenEncryptionService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        // Prefer env var, fallback to property
        String keyB64 = firstNonBlank(
                environment.getProperty("SECURITY_TOKEN_ENCRYPTION_KEY"),
                environment.getProperty("security.token-encryption-key")
        );

        boolean isProd = isProdProfileActive();

        if (isBlank(keyB64)) {
            // In prod we should fail fast
            if (isProd) {
                throw new IllegalStateException(
                        "SECURITY_TOKEN_ENCRYPTION_KEY is required in production to encrypt broker tokens at rest."
                );
            }
            // In non-prod we allow null key (encryption disabled), but we keep app running.
            this.secretKey = null;
            return;
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyB64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SECURITY_TOKEN_ENCRYPTION_KEY must be Base64 encoded.", e);
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "SECURITY_TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256). Got: " + keyBytes.length
            );
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a value only if:
     * - not null/blank
     * - not already encrypted
     * - secret key is configured
     */
    public String encryptIfNeeded(String plain) {
        if (isBlank(plain)) return plain;
        if (isEncrypted(plain)) return plain;

        // if key not configured (dev/local without key), do nothing
        if (secretKey == null) return plain;

        return encrypt(plain);
    }

    /**
     * Decrypts a value only if:
     * - it is encrypted (enc:v1:...)
     * - secret key is configured
     *
     * If the value is NOT encrypted, returns as-is.
     */
    public String decryptIfNeeded(String value) {
        if (isBlank(value)) return value;
        if (!isEncrypted(value)) return value;

        if (secretKey == null) {
            // If value is encrypted but key is missing, we cannot decrypt — fail clearly.
            throw new IllegalStateException("Encrypted token present but SECURITY_TOKEN_ENCRYPTION_KEY is not configured.");
        }

        return decrypt(value);
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Optional helper if you ever want to detect JSON-ish strings.
     * This is safe and DOES NOT contain invalid escapes.
     */
    public boolean looksLikeJson(String value) {
        if (value == null) return false;
        String v = value.trim();
        if ((v.startsWith("{") && v.endsWith("}")) || (v.startsWith("[") && v.endsWith("]"))) return true;
        // Lightweight heuristic
        return v.contains("\":"); // ✅ FIXED: valid Java string
    }

    // -------------------- internal crypto --------------------

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            // payload = iv + ciphertext
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token.", e);
        }
    }

    private String decrypt(String encrypted) {
        try {
            String b64 = encrypted.substring(PREFIX.length());
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
            throw new IllegalStateException("Failed to decrypt token. Check SECURITY_TOKEN_ENCRYPTION_KEY.", e);
        }
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
}
