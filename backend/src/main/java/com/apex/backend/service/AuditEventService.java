package com.apex.backend.service;

import com.apex.backend.model.AuditEvent;
import com.apex.backend.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void recordEvent(Long userId, String eventType, String action, String description, Object metadata) {
        try {
            String payload = metadata == null ? null : objectMapper.writeValueAsString(metadata);
            AuditEvent event = AuditEvent.builder()
                    .userId(userId)
                    .eventType(eventType)
                    .action(action)
                    .description(description)
                    .metadata(payload)
                    .correlationId(MDC.get("correlationId"))
                    .createdAt(Instant.now())
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record audit event {}:{} - {}", eventType, action, e.getMessage());
        }
    }
}
