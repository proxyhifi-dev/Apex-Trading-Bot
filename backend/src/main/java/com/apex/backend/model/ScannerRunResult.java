package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "scanner_run_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerRunResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ScannerRun run;

    @Column(nullable = false)
    private String symbol;

    private Double score;

    private String grade;

    private Double entryPrice;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;
}
