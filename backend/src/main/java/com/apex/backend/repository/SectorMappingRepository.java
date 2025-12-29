package com.apex.backend.repository;

import com.apex.backend.model.SectorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SectorMappingRepository extends JpaRepository<SectorMapping, Long> {
    Optional<SectorMapping> findBySymbol(String symbol);
}