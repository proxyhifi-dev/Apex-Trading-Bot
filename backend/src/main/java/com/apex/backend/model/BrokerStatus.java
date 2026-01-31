package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "broker_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String broker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String reason;

    private LocalDateTime degradedAt;

    private LocalDateTime nextAllowedAt;

    private LocalDateTime updatedAt;

    public enum Status {
        NORMAL,
        DEGRADED
    }
}
