package com.apex.backend.service;

import com.apex.backend.model.RiskEvent;
import com.apex.backend.repository.RiskEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RiskEventService {

    private final RiskEventRepository riskEventRepository;

    public RiskEvent record(Long userId, String type, String description, String metadata) {
        if (userId == null) {
            return null;
        }
        RiskEvent event = RiskEvent.builder()
                .userId(userId)
                .type(type)
                .description(description)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
        return riskEventRepository.save(event);
    }
}
