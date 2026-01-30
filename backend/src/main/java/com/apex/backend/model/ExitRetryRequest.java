package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "exit_retry_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExitRetryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private boolean paper;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean resolved;

    private Instant nextAttemptAt;

    private Instant lastAttemptAt;

    @Column(length = 255)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private boolean dlqLogged;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
