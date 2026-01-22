package com.apex.backend.repository;

import com.apex.backend.model.ScannerRunResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScannerRunResultRepository extends JpaRepository<ScannerRunResult, Long> {
    List<ScannerRunResult> findByRunIdOrderByScoreDesc(Long runId);
}
