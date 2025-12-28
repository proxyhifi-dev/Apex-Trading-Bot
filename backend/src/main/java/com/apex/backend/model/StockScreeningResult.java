package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_screening_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private TradingStrategy strategy;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Double currentPrice;

    @Column
    private Integer signalScore;

    @Column
    private Boolean hasEntrySignal;

    @Column(nullable = false)
    private LocalDateTime scanTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus;

    // Enums
    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, EXECUTED
    }
}
