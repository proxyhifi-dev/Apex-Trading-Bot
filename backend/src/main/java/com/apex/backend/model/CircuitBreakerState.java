package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_breaker_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerState {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean globalHalt;

    @Column(nullable = false)
    private boolean entryHalt;

    private LocalDateTime pauseUntil;

    private String reason;

    @Column(nullable = false)
    private int consecutiveLosses;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
