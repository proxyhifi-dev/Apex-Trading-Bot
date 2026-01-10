package com.apex.backend.service;

import com.apex.backend.model.DecisionAudit;
import com.apex.backend.repository.DecisionAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DecisionAuditService {

    private final DecisionAuditRepository decisionAuditRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(String symbol, String timeframe, String decisionType, Map<String, Object> details) {
        String json = null;
        try {
            json = objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize decision audit for {}", decisionType, e);
        }
        DecisionAudit audit = DecisionAudit.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .decisionType(decisionType)
                .decisionTime(LocalDateTime.now())
                .details(json)
                .build();
        decisionAuditRepository.save(audit);
    }
}
