package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "watchlist_stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private boolean active;

    // ✅ FIXED: Ensure no CascadeType.ALL here
    @ManyToOne
    @JoinColumn(name = "strategy_id", nullable = false)
    private TradingStrategy strategy;
}