package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "scanner_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String universeType;

    @Column(length = 4000)
    private String universePayload;

    private String strategyId;

    @Column(length = 4000)
    private String optionsPayload;

    @Column(nullable = false)
    private boolean dryRun;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 2000)
    private String errorMessage;

    private Integer totalSymbols;
    private Integer passedStage1;
    private Integer passedStage2;
    private Integer finalSignals;

    // ✅ FIX: explicit column names to match Flyway migration
    @Column(name = "rejected_stage1_reason_counts", length = 4000)
    private String rejectedStage1ReasonCounts;

    @Column(name = "rejected_stage2_reason_counts", length = 4000)
    private String rejectedStage2ReasonCounts;

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
