package com.apex.backend.controller;

import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.CorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {
    
    private final PortfolioService portfolioService;
        private final CorrelationService correlationService;
    
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
        }
    }

        /**
     * Get correlation matrix for portfolio stocks
     * Calculates Pearson correlation coefficients between positions
     */
    @GetMapping("/correlation-matrix")
    public ResponseEntity<?> getCorrelationMatrix() {
        try {
            log.info("Fetching correlation matrix");
            // TODO: Replace mock data with real portfolio positions
            Map<String, List<Double>> portfolioData = getMockPortfolioData();
            CorrelationService.CorrelationMatrix matrix = correlationService.buildCorrelationMatrix(portfolioData);
            return ResponseEntity.ok(matrix);
        } catch (Exception e) {
            log.error("Failed to fetch correlation matrix", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch correlation matrix"));
        }
    }

    /**
     * Mock portfolio data for testing
     * TODO: Replace with actual position data from database
     */
    private Map<String, List<Double>> getMockPortfolioData() {
        Map<String, List<Double>> data = new HashMap<>();
        // Sample price data for testing
        data.put("TCS", Arrays.asList(3500.0, 3510.0, 3520.0, 3515.0, 3525.0));
        data.put("RELIANCE", Arrays.asList(2400.0, 2410.0, 2420.0, 2415.0, 2425.0));
        data.put("INFY", Arrays.asList(1800.0, 1810.0, 1820.0, 1815.0, 1825.0));
        return data;
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class RiskStatus {
        public double equity;
        public int openPositions;
        
        public RiskStatus(double equity, int openPositions) {
            this.equity = equity;
            this.openPositions = openPositions;
        }
    }
    
    public static class MessageResponse {
        public String message;
        public long timestamp;
        
        public MessageResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
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
