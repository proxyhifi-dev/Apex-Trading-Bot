package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "bot_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private boolean running;

    private Instant lastScanAt;

    private Instant nextScanAt;

    @Column(length = 1000)
    private String lastError;

    private Instant lastErrorAt;

    private Boolean threadAlive;

    private Integer queueDepth;
}
