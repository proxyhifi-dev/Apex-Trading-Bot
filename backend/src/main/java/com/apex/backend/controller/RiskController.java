package com.apex.backend.controller;

import com.apex.backend.service.CorrelationService;
import com.apex.backend.service.EmergencyStopService;
import com.apex.backend.service.PortfolioService;
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
    private final EmergencyStopService emergencyStopService;
    
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
            EmergencyStopService.EmergencyStopResult result = emergencyStopService.triggerEmergencyStop("MANUAL_EMERGENCY_STOP");
            return ResponseEntity.ok(new EmergencyStopResponse(
                    "Emergency stop activated - all trading halted",
                    result.closedTrades(),
                    result.globalHaltEnabled()
            ));
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
            Map<String, List<Double>> portfolioData = portfolioService.getPortfolioPriceSeries(false, 40);
            if (portfolioData.isEmpty()) {
                return ResponseEntity.ok(new CorrelationService.CorrelationMatrix(List.of(), new double[0][0]));
            }
            CorrelationService.CorrelationMatrix matrix = correlationService.buildCorrelationMatrix(portfolioData);
            return ResponseEntity.ok(matrix);
        } catch (Exception e) {
            log.error("Failed to fetch correlation matrix", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch correlation matrix"));
        }
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

    public static class EmergencyStopResponse {
        public String message;
        public int closedTrades;
        public boolean globalHalt;
        public long timestamp;

        public EmergencyStopResponse(String message, int closedTrades, boolean globalHalt) {
            this.message = message;
            this.closedTrades = closedTrades;
            this.globalHalt = globalHalt;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
