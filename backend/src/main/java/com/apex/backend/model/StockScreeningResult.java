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

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer signalScore;

    @Column(nullable = false)
    private String grade;

    @Column(nullable = false)
    private Double entryPrice;

    @Column
    private Double stopLoss;

    @Column(nullable = false)
    private LocalDateTime scanTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(length = 1000)
    private String analysisReason;

    // Indicators snapshot
    private Double macdValue;
    private Double rsiValue;
    private Double adxValue;

    // ✅ FIXED: Removed 'cascade = CascadeType.ALL' which caused the crash
    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private TradingStrategy strategy;

    // Helper method
    public Double getCurrentPrice() {
        return entryPrice;
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, EXECUTED
    }
}