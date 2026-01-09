package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "paper_portfolio_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperPortfolioStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private Double winRate;
    private Double netPnl;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
