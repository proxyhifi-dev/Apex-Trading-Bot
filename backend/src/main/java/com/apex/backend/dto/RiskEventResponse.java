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
public class RiskEventResponse {

    private Long id;
    private String type;
    private String description;
    private String metadata;
    private Instant createdAt;
}
