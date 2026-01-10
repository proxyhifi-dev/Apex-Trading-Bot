package com.apex.backend.service;

import com.apex.backend.model.BrokerStatus;
import com.apex.backend.repository.BrokerStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BrokerStatusService {

    private final BrokerStatusRepository brokerStatusRepository;

    @Transactional
    public void markDegraded(String broker, String reason) {
        BrokerStatus status = brokerStatusRepository.findByBroker(broker)
                .orElseGet(() -> BrokerStatus.builder()
                        .broker(broker)
                        .status(BrokerStatus.Status.DEGRADED)
                        .degradedAt(LocalDateTime.now())
                        .build());
        status.setStatus(BrokerStatus.Status.DEGRADED);
        status.setReason(reason);
        if (status.getDegradedAt() == null) {
            status.setDegradedAt(LocalDateTime.now());
        }
        status.setUpdatedAt(LocalDateTime.now());
        brokerStatusRepository.save(status);
    }

    @Transactional
    public void markNormal(String broker) {
        BrokerStatus status = brokerStatusRepository.findByBroker(broker)
                .orElseGet(() -> BrokerStatus.builder()
                        .broker(broker)
                        .status(BrokerStatus.Status.NORMAL)
                        .build());
        status.setStatus(BrokerStatus.Status.NORMAL);
        status.setReason(null);
        status.setUpdatedAt(LocalDateTime.now());
        brokerStatusRepository.save(status);
    }
}
