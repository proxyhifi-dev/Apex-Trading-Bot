package com.apex.backend.service;

import com.apex.backend.model.FailedOperation;
// ✅ FIXED: Added missing import
import com.apex.backend.repository.FailedOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final FailedOperationRepository repo;

    public void logFailure(String type, String details, String error) {
        log.error("💀 DLQ Entry: [{}] {} -> {}", type, details, error);

        FailedOperation op = FailedOperation.builder()
                .operationType(type)
                .details(details)
                .errorMessage(error)
                .timestamp(LocalDateTime.now())
                .resolved(false)
                .retryCount(0)
                .build();

        repo.save(op);
    }

    public void resolve(Long id) {
        repo.findById(id).ifPresent(op -> {
            op.setResolved(true);
            repo.save(op);
            log.info("✅ DLQ Operation {} marked as resolved.", id);
        });
    }
}
