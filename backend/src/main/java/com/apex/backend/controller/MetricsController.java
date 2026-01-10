package com.apex.backend.controller;

import com.apex.backend.dto.MetricsSnapshot;
import com.apex.backend.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping
    public MetricsSnapshot snapshot() {
        return metricsService.snapshot();
    }
}
