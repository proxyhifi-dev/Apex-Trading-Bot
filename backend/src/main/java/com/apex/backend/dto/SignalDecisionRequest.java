package com.apex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDecisionRequest {

    @NotBlank
    private String reason;

    private String notes;

    @Builder.Default
    private boolean override = false;
}
