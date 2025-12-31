package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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

    // ✅ FIX: Added this endpoint to match Frontend calls
    @GetMapping("/equity-curve")
    public ResponseEntity<List<Object>> getEquityCurve(@RequestParam(defaultValue = "PAPER") String type) {
        // Return empty list for now to prevent 404 errors
        return ResponseEntity.ok(new ArrayList<>());
    }
}