package com.apex.backend.repository;

import com.apex.backend.model.WatchlistStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistStockRepository extends JpaRepository<WatchlistStock, Long> {

    @Query("""
        select ws.symbol
        from WatchlistStock ws
        where ws.active = true
          and ws.strategyId = :strategyId
        order by ws.id asc
    """)
    List<String> findActiveSymbolsByStrategyId(@Param("strategyId") Long strategyId);
}
