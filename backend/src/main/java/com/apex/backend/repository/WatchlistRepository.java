package com.apex.backend.repository;

import com.apex.backend.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    Optional<Watchlist> findByUserIdAndIsDefaultTrue(Long userId);
}
