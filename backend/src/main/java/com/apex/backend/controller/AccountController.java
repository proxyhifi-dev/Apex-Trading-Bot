package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AccountController {
    
    @Value("${apex.trading.capital:100000}")
    private double initialCapital;
    
    /**
     * Get user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            log.info("Fetching user profile");
            UserProfileDTO profile = UserProfileDTO.builder()
                    .name("Trading Account")
                    .availableFunds(initialCapital)
                    .totalInvested(0.0)
                    .currentValue(initialCapital)
                    .todaysPnl(0.0)
                    .holdings(new ArrayList<>())
                    .build();
            log.info("Successfully retrieved profile");
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Failed to fetch profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch account data"));
        }
    }
    
    /**
     * Get account summary (PAPER or LIVE)
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(defaultValue = "PAPER") String type) {
        try {
            log.info("Fetching account summary for type: {}", type);
            
            // Validate type parameter
            if (!type.equalsIgnoreCase("PAPER") && !type.equalsIgnoreCase("LIVE")) {
                log.warn("Invalid account type requested: {}", type);
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Account type must be PAPER or LIVE"));
            }
            
            UserProfileDTO summary;
            if ("PAPER".equalsIgnoreCase(type)) {
                summary = UserProfileDTO.builder()
                        .name("Paper Trading Account")
                        .availableFunds(initialCapital)
                        .totalInvested(0.0)
                        .currentValue(initialCapital)
                        .todaysPnl(0.0)
                        .holdings(new ArrayList<>())
                        .build();
                log.info("Returned PAPER trading account summary");
            } else {
                summary = UserProfileDTO.builder()
                        .name("Live Trading Account")
                        .availableFunds(0.0)
                        .totalInvested(0.0)
                        .currentValue(0.0)
                        .todaysPnl(0.0)
                        .holdings(new ArrayList<>())
                        .build();
                log.info("Returned LIVE trading account summary");
            }
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to fetch account summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch account summary"));
        }
    }
    
    /**
     * Get capital information
     */
    @GetMapping("/capital")
    public ResponseEntity<?> getCapital() {
        try {
            log.info("Fetching capital information");
            return ResponseEntity.ok(new CapitalInfo(initialCapital, initialCapital, 0.0));
        } catch (Exception e) {
            log.error("Failed to fetch capital info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch capital information"));
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class ErrorResponse {
        public String error;
        public long timestamp;
        
        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static class CapitalInfo {
        public double initialCapital;
        public double availableCapital;
        public double usedCapital;
        
        public CapitalInfo(double initialCapital, double availableCapital, double usedCapital) {
            this.initialCapital = initialCapital;
            this.availableCapital = availableCapital;
            this.usedCapital = usedCapital;
        }
    }
}
