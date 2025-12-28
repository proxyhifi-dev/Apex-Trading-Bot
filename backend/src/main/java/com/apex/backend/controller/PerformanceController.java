package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/metrics")
    public ResponseEntity<PerformanceMetrics> getPerformanceMetrics() {
        return ResponseEntity.ok(performanceService.calculateMetrics());
    }
}
