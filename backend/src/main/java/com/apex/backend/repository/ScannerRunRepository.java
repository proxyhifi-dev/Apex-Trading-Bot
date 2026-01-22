package com.apex.backend.repository;

import com.apex.backend.model.ScannerRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScannerRunRepository extends JpaRepository<ScannerRun, Long> {
    Optional<ScannerRun> findByIdAndUserId(Long id, Long userId);
}
