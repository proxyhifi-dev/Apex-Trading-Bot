package com.apex.backend.repository;

import com.apex.backend.model.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {}
