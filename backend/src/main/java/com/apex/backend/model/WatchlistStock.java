package com.apex.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "watchlist_stocks")
public class WatchlistStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @ManyToOne
    @JoinColumn(name = "strategy_id", nullable = false)
    private TradingStrategy strategy;

    @Column(nullable = false)
    private boolean active = true;  // ✅ Field name is "active" not "isActive"
}
