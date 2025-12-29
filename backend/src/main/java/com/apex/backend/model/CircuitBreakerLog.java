package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_breaker_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime triggerTime;
    private String reason;
    private Double triggeredValue; // e.g., Daily Loss Amount
    private String actionTaken;    // e.g., "HALT_TRADING"
}