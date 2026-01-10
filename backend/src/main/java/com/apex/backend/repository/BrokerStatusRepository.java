package com.apex.backend.repository;

import com.apex.backend.model.BrokerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrokerStatusRepository extends JpaRepository<BrokerStatus, Long> {
    Optional<BrokerStatus> findByBroker(String broker);
}
