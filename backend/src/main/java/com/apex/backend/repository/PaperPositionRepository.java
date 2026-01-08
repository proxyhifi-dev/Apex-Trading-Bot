package com.apex.backend.repository;

import com.apex.backend.model.PaperPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {
    List<PaperPosition> findByStatus(String status);
}
