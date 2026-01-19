package com.apex.backend.repository;

import com.apex.backend.model.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {
    List<WatchlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WatchlistEntry> findByUserIdAndSymbolIgnoreCaseAndExchangeIgnoreCase(Long userId, String symbol, String exchange);
}
