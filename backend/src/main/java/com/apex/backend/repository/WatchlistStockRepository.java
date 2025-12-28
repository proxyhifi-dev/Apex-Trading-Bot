package com.apex.backend.repository;

import com.apex.backend.model.WatchlistStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchlistStockRepository extends JpaRepository<WatchlistStock, Long> {
    List<WatchlistStock> findByActiveTrue();
}
