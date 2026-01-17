package com.apex.backend.repository;

import com.apex.backend.model.BotState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotStateRepository extends JpaRepository<BotState, Long> {
    Optional<BotState> findByUserId(Long userId);
}
