package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.service.FyersAPIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final FyersAPIService fyersAPIService;

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    /**
     * Get user profile with real Fyers account data
     * FIXED: No longer returns hardcoded data
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            log.info("Fetching user profile from Fyers API");

            // Get real account data from Fyers API
            UserProfileDTO profile = fyersAPIService.getAccountProfile();

            log.info("Successfully retrieved profile for user: {}", profile.getName());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Failed to fetch profile from Fyers API", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Failed to fetch account data from Fyers. Please check API credentials."));
        }
    }

    /**
     * Get account summary (PAPER or LIVE)
     * FIXED: Now validates type parameter and returns correct data
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
                summary = fyersAPIService.getPaperAccountProfile();
                log.info("Returned PAPER trading account summary");
            } else {
                summary = fyersAPIService.getLiveAccountProfile();
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
     * Refresh account data from Fyers API
     * FIXED: New endpoint to manually refresh data
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccountData() {
        try {
            log.info("Refreshing account data from Fyers API");
            fyersAPIService.refreshAccountData();
            return ResponseEntity.ok(new SuccessResponse("Account data refreshed successfully"));
        } catch (Exception e) {
            log.error("Failed to refresh account data", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Failed to refresh account data"));
        }
    }

    // Error response wrapper class
    public static class ErrorResponse {
        public String error;
        public long timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // Success response wrapper class
    public static class SuccessResponse {
        public String message;
        public long timestamp;

        public SuccessResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
