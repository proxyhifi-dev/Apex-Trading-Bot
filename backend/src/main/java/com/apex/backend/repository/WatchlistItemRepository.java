package com.apex.backend.repository;

import com.apex.backend.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByWatchlistIdOrderByCreatedAtAsc(Long watchlistId);
    List<WatchlistItem> findByWatchlistIdAndStatusOrderByCreatedAtAsc(Long watchlistId, WatchlistItem.Status status);
    Optional<WatchlistItem> findByWatchlistIdAndSymbol(Long watchlistId, String symbol);
    long countByWatchlistId(Long watchlistId);
    long countByWatchlistIdAndStatus(Long watchlistId, WatchlistItem.Status status);
    List<WatchlistItem> findByStatus(WatchlistItem.Status status);
}
