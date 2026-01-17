package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotHealthResponse {

    private boolean running;
    private Instant lastScanAt;
    private Instant nextScanAt;
    private String lastError;
    private Instant lastErrorAt;
    private boolean threadAlive;
    private Integer queueDepth;
}
