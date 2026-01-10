package com.apex.backend.repository;

import com.apex.backend.model.ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationRunRepository extends JpaRepository<ValidationRun, Long> {
    List<ValidationRun> findByUserIdOrderByCreatedAtDesc(Long userId);
}
