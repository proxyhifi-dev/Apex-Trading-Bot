package com.apex.backend.controller;

import com.apex.backend.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final PortfolioService portfolioService;

    @GetMapping("/status")
    public ResponseEntity<?> getRiskStatus() {
        try {
            log.info("Fetching risk status");
            double equity = portfolioService.getAvailableEquity(false);
            int positions = portfolioService.getOpenPositionCount(false);

            return ResponseEntity.ok(new RiskStatus(equity, positions));
        } catch (Exception e) {
            log.error("Failed to fetch risk status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch risk status"));
        }
    }

    public static class RiskStatus {
        public double equity;
        public int openPositions;

        public RiskStatus(double equity, int openPositions) {
            this.equity = equity;
            this.openPositions = openPositions;
        }
    }

    public static class ErrorResponse {
        public String error;
        public long timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
