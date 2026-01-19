package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BrokerStatusResponse {
    private boolean connected;
    private String broker;
    private LocalDateTime lastCheckedAt;
    private String accountId;
    private String error;
}
