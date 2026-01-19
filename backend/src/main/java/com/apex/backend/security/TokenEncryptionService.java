package com.apex.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class TokenEncryptionService {

    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(@Value("${security.token-encryption-key:}") String key) {
        this.secretKey = buildKey(key);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        if (encrypted.isBlank()) {
            return encrypted;
        }
        if (!encrypted.contains(":")) {
            return encrypted;
        }
        try {
            String[] parts = encrypted.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("Invalid encrypted token format");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token", e);
        }
    }

    public boolean looksEncrypted(String value) {
        return value != null && value.contains(\":\");
    }

    private SecretKey buildKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException("SECURITY_TOKEN_ENCRYPTION_KEY is required for token encryption");
        }
        byte[] keyBytes = decodeKey(rawKey);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("SECURITY_TOKEN_ENCRYPTION_KEY must be 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] decodeKey(String rawKey) {
        try {
            return Base64.getDecoder().decode(rawKey);
        } catch (IllegalArgumentException e) {
            log.warn("Token encryption key not base64 encoded; using raw UTF-8 bytes");
            return rawKey.getBytes(StandardCharsets.UTF_8);
        }
    }
}
