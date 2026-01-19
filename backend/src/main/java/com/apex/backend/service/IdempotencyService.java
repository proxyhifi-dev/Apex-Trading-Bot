package com.apex.backend.service;

import com.apex.backend.exception.ConflictException;
import com.apex.backend.model.IdempotencyKey;
import com.apex.backend.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public <T> T execute(Long userId, String idempotencyKey, Object request, Class<T> responseType, Supplier<T> supplier) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return supplier.get();
        }
        String requestHash = hashRequest(request);
        IdempotencyKey key = IdempotencyKey.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotencyKey.Status.IN_PROGRESS)
                .correlationId(MDC.get("correlationId"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            idempotencyKeyRepository.save(key);
        } catch (DataIntegrityViolationException ex) {
            return loadExisting(userId, idempotencyKey, requestHash, responseType);
        }

        try {
            T response = supplier.get();
            key.setStatus(IdempotencyKey.Status.COMPLETED);
            key.setResponsePayload(serialize(response));
            key.setUpdatedAt(Instant.now());
            idempotencyKeyRepository.save(key);
            return response;
        } catch (RuntimeException ex) {
            key.setStatus(IdempotencyKey.Status.FAILED);
            key.setErrorMessage(ex.getMessage());
            key.setUpdatedAt(Instant.now());
            idempotencyKeyRepository.save(key);
            throw ex;
        }
    }

    private <T> T loadExisting(Long userId, String idempotencyKey, String requestHash, Class<T> responseType) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isEmpty()) {
            throw new ConflictException("Idempotency key collision");
        }
        IdempotencyKey stored = existing.get();
        if (!stored.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key reused with different payload");
        }
        if (stored.getStatus() == IdempotencyKey.Status.IN_PROGRESS) {
            throw new ConflictException("Idempotency key already in progress");
        }
        if (stored.getStatus() == IdempotencyKey.Status.FAILED) {
            String message = stored.getErrorMessage() != null ? stored.getErrorMessage() : "Idempotent request previously failed";
            throw new ConflictException(message);
        }
        auditEventService.recordEvent(userId, "IDEMPOTENCY", "REPLAY",
                "Idempotent replay served from cache",
                java.util.Map.of("idempotencyKey", idempotencyKey));
        return deserialize(stored.getResponsePayload(), responseType);
    }

    private String hashRequest(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = objectMapper.writeValueAsString(request);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash idempotent request", e);
        }
    }

    private String serialize(Object response) {
        try {
            return response == null ? null : objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private <T> T deserialize(String payload, Class<T> responseType) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (Exception e) {
            log.warn("Failed to deserialize idempotent response: {}", e.getMessage());
            throw new ConflictException("Stored idempotent response unreadable");
        }
    }
}
