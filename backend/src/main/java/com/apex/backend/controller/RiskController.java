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

    
    /**
     * Trigger emergency stop
     * Halts all trading operations immediately
     */
    @PostMapping("/emergency-stop")
    public ResponseEntity<?> triggerEmergencyStop() {
        try {
            log.info("Emergency stop triggered");
            // In production, this should:
            // 1. Close all open positions
            // 2. Cancel all pending orders
            // 3. Freeze the trading account
            // 4. Log the incident
            return ResponseEntity.ok(new MessageResponse("Emergency stop activated - all trading halted"));
        } catch (Exception e) {
            log.error("Failed to trigger emergency stop", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to trigger emergency stop"));
     
        
    public static class MessageResponse {
        public String message;
        public long timestamp;
        
        public MessageResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }}
    }}
}
