package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer signalScore;

    @Column(nullable = false)
    private String grade;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal stopLoss;

    @Column(nullable = false)
    private LocalDateTime scanTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(length = 1000)
    private String analysisReason;

    @Column(length = 1000)
    private String decisionReason;

    @Column(length = 2000)
    private String decisionNotes;

    @Column(length = 100)
    private String decidedBy;

    private LocalDateTime decisionAt;

    // Indicators snapshot
    private Double macdValue;
    private Double rsiValue;
    private Double adxValue;

    // ✅ FIXED: Removed 'cascade = CascadeType.ALL' which caused the crash
    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private TradingStrategy strategy;

    // Helper method
    public BigDecimal getCurrentPrice() {
        return entryPrice;
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, EXECUTED
    }
}
