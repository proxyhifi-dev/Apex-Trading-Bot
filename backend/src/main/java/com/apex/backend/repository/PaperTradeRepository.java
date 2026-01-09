package com.apex.backend.repository;

import com.apex.backend.model.PaperTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaperTradeRepository extends JpaRepository<PaperTrade, Long> {
    List<PaperTrade> findByUserIdAndStatus(Long userId, String status);
    List<PaperTrade> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
