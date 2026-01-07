package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiConfigDTO {
    private String apiBaseUrl;
    private String wsBaseUrl;
    private List<UiEndpointDTO> endpoints;
    private Map<String, List<String>> entityFields;
}
