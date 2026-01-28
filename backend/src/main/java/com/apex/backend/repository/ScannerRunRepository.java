package com.apex.backend.repository;

import com.apex.backend.model.ScannerRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScannerRunRepository extends JpaRepository<ScannerRun, Long> {
    Optional<ScannerRun> findByIdAndUserId(Long id, Long userId);

    List<ScannerRun> findByStatusAndCreatedAtBefore(ScannerRun.Status status, Instant createdAt);

    List<ScannerRun> findByStatusAndStartedAtBefore(ScannerRun.Status status, Instant startedAt);

    Optional<ScannerRun> findTopByOrderByIdDesc();

    Optional<ScannerRun> findTopByUserIdOrderByIdDesc(Long userId);
}
