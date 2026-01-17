package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValidationResult {

    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private NormalizedOrderDTO normalizedOrder;
}
