package com.apex.backend.repository;

import com.apex.backend.model.PaperTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaperTradeRepository extends JpaRepository<PaperTrade, Long> {}
