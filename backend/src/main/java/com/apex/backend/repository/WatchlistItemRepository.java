package com.apex.backend.repository;

import com.apex.backend.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByWatchlistIdOrderByCreatedAtAsc(Long watchlistId);
    Optional<WatchlistItem> findByWatchlistIdAndSymbol(Long watchlistId, String symbol);
    long countByWatchlistId(Long watchlistId);
}
