package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long backtestResultId;

    @Column(nullable = false, length = 4000)
    private String metricsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
