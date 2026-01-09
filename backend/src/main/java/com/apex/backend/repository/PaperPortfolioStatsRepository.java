package com.apex.backend.repository;

import com.apex.backend.model.PaperPortfolioStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaperPortfolioStatsRepository extends JpaRepository<PaperPortfolioStats, Long> {
    Optional<PaperPortfolioStats> findTopByUserIdOrderByUpdatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
}
