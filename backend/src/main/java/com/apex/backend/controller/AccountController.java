package com.apex.backend.controller;

import com.apex.backend.dto.ApiErrorResponse;
import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    
    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;
    private final PaperTradingService paperTradingService;
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * Get user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestParam(defaultValue = "live") String mode,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching user profile");
            if ("paper".equalsIgnoreCase(mode)) {
                PaperPortfolioStats stats = paperTradingService.getStats();
                UserProfileDTO profile = UserProfileDTO.builder()
                        .name("Paper Trading Account")
                        .availableFunds(paperTradingService.getAvailableFunds(initialCapital))
                        .totalInvested(0.0)
                        .currentValue(paperTradingService.getAvailableFunds(initialCapital))
                        .todaysPnl(stats.getNetPnl() != null ? stats.getNetPnl() : 0.0)
                        .holdings(new ArrayList<>())
                        .build();
                return ResponseEntity.ok(profile);
            }
            String token = resolveFyersToken(authHeader);
            Map<String, Object> profile = fyersService.getProfile(token);
            log.info("Successfully retrieved profile");
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Failed to fetch profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch account data", e.getMessage()));
        }
    }
    
    /**
     * Get account summary (PAPER or LIVE)
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(defaultValue = "paper") String mode,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching account summary for mode: {}", mode);

            if ("paper".equalsIgnoreCase(mode)) {
                PaperPortfolioStats stats = paperTradingService.getStats();
                UserProfileDTO summary = UserProfileDTO.builder()
                        .name("Paper Trading Account")
                        .availableFunds(paperTradingService.getAvailableFunds(initialCapital))
                        .totalInvested(0.0)
                        .currentValue(paperTradingService.getAvailableFunds(initialCapital))
                        .todaysPnl(stats.getNetPnl() != null ? stats.getNetPnl() : 0.0)
                        .holdings(new ArrayList<>())
                        .build();
                return ResponseEntity.ok(summary);
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getFunds(token));
        } catch (Exception e) {
            log.error("Failed to fetch account summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch account summary", e.getMessage()));
        }
    }

    @GetMapping("/holdings")
    public ResponseEntity<?> getHoldings(@RequestParam(defaultValue = "live") String mode,
                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if ("paper".equalsIgnoreCase(mode)) {
                return ResponseEntity.ok(paperTradingService.getOpenPositions());
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getHoldings(token));
        } catch (Exception e) {
            log.error("Failed to fetch holdings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch holdings", e.getMessage()));
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
                    .body(new ApiErrorResponse("Failed to fetch capital information", e.getMessage()));
        }
    }
    
    // ==================== INNER CLASSES ====================
    
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

    private String resolveFyersToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization header");
        }
        String jwt = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Fyers account not linked");
        }
        return token;
    }
}
