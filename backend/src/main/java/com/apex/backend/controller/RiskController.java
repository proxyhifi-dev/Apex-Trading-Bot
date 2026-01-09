package com.apex.backend.controller;

import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.CorrelationService;
import com.apex.backend.service.EmergencyStopService;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final SettingsService settingsService;
    
    @GetMapping("/status")
    public ResponseEntity<?> getRiskStatus(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching risk status");
            double equity = portfolioService.getAvailableEquity(isPaper, userId);
            int positions = portfolioService.getOpenPositionCount(isPaper, userId);
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
    public ResponseEntity<?> triggerEmergencyStop(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Emergency stop triggered");
            EmergencyStopService.EmergencyStopResult result = emergencyStopService.triggerEmergencyStop(
                    userId,
                    isPaper,
                    "MANUAL_EMERGENCY_STOP"
            );
            return ResponseEntity.ok(new EmergencyStopResponse(
                    "Emergency stop activated - trading halted for this account",
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
    public ResponseEntity<?> getCorrelationMatrix(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching correlation matrix");
            Map<String, List<Double>> portfolioData = portfolioService.getPortfolioPriceSeries(isPaper, 40, userId);
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

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
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
