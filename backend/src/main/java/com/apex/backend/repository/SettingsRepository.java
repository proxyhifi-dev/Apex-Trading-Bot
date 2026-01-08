package com.apex.backend.repository;

import com.apex.backend.model.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Settings, Long> {
    Optional<Settings> findFirstByOrderByIdAsc();
}
