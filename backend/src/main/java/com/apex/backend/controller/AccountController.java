package com.apex.backend.controller;

import com.apex.backend.dto.AccountOverviewDTO;
import com.apex.backend.dto.ApiErrorResponse;
import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;
    private final PaperTradingService paperTradingService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SettingsService settingsService;
    
    /**
     * Get user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching user profile");
            Long userId = resolveUserId(authHeader);
            String mode = settingsService.getModeForUser(userId);
            if ("paper".equalsIgnoreCase(mode)) {
                PaperPortfolioStats stats = paperTradingService.getStats(userId);
                PaperAccount account = paperTradingService.getAccount(userId);
                UserProfileDTO profile = UserProfileDTO.builder()
                        .name("Paper Trading Account")
                        .availableFunds(account.getCashBalance())
                        .totalInvested(account.getReservedMargin())
                        .currentValue(account.getCashBalance() + account.getReservedMargin() + account.getUnrealizedPnl())
                        .todaysPnl(stats.getNetPnl() != null ? stats.getNetPnl() : 0.0)
                        .holdings(new ArrayList<>())
                        .build();
                return ResponseEntity.ok(profile);
            }
            String token = resolveFyersToken(authHeader);
            Map<String, Object> profile = fyersService.getProfile(token);
            log.info("Successfully retrieved profile");
            return ResponseEntity.ok(profile);
        } catch (IllegalStateException e) {
            log.warn("Failed to fetch profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("Failed to fetch account data", e.getMessage()));
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
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            String mode = settingsService.getModeForUser(userId);
            log.info("Fetching account summary for mode: {}", mode);

            if ("paper".equalsIgnoreCase(mode)) {
                PaperPortfolioStats stats = paperTradingService.getStats(userId);
                PaperAccount account = paperTradingService.getAccount(userId);
                UserProfileDTO summary = UserProfileDTO.builder()
                        .name("Paper Trading Account")
                        .availableFunds(account.getCashBalance())
                        .totalInvested(account.getReservedMargin())
                        .currentValue(account.getCashBalance() + account.getReservedMargin() + account.getUnrealizedPnl())
                        .todaysPnl(stats.getNetPnl() != null ? stats.getNetPnl() : 0.0)
                        .holdings(new ArrayList<>())
                        .build();
                return ResponseEntity.ok(summary);
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getFunds(token));
        } catch (IllegalStateException e) {
            log.warn("Failed to fetch account summary: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("Failed to fetch account summary", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch account summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch account summary", e.getMessage()));
        }
    }

    @GetMapping("/holdings")
    public ResponseEntity<?> getHoldings(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            String mode = settingsService.getModeForUser(userId);
            if ("paper".equalsIgnoreCase(mode)) {
                return ResponseEntity.ok(paperTradingService.getOpenPositions(userId));
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getHoldings(token));
        } catch (IllegalStateException e) {
            log.warn("Failed to fetch holdings: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("Failed to fetch holdings", e.getMessage()));
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
    public ResponseEntity<?> getCapital(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            String mode = settingsService.getModeForUser(userId);
            if ("paper".equalsIgnoreCase(mode)) {
                PaperAccount account = paperTradingService.getAccount(userId);
                return ResponseEntity.ok(new CapitalInfo(account.getStartingCapital(), account.getCashBalance(), account.getReservedMargin()));
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getFunds(token));
        } catch (Exception e) {
            log.error("Failed to fetch capital info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch capital information", e.getMessage()));
        }
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            String mode = settingsService.getModeForUser(userId);
            if ("paper".equalsIgnoreCase(mode)) {
                PaperAccount account = paperTradingService.getAccount(userId);
                double pnl = account.getRealizedPnl() + account.getUnrealizedPnl();
                java.util.List<com.apex.backend.model.PaperPosition> openPositions = paperTradingService.getOpenPositions(userId);
                return ResponseEntity.ok(AccountOverviewDTO.builder()
                        .mode(mode)
                        .profile(AccountOverviewDTO.ProfileDTO.builder()
                                .name("Paper Trading Account")
                                .brokerId("PAPER")
                                .build())
                        .funds(AccountOverviewDTO.FundsDTO.builder()
                                .cash(account.getCashBalance())
                                .used(account.getReservedMargin())
                                .free(account.getCashBalance())
                                .build())
                        .holdings(openPositions)
                        .positions(openPositions)
                        .todayPnl(pnl)
                        .lastUpdatedAt(account.getUpdatedAt())
                        .dataSource("PAPER")
                        .build());
            }

            String token = resolveFyersToken(authHeader);
            Map<String, Object> profile = fyersService.getProfile(token);
            Map<String, Object> funds = fyersService.getFunds(token);
            Map<String, Object> holdings = fyersService.getHoldings(token);
            Map<String, Object> positions = fyersService.getPositions(token);
            return ResponseEntity.ok(AccountOverviewDTO.builder()
                    .mode(mode)
                    .profile(AccountOverviewDTO.ProfileDTO.builder()
                            .name(extractProfileName(profile))
                            .brokerId(extractProfileId(profile))
                            .build())
                    .funds(AccountOverviewDTO.FundsDTO.builder()
                            .cash(extractFundValue(funds, "cash"))
                            .used(extractFundValue(funds, "used"))
                            .free(extractFundValue(funds, "available"))
                            .build())
                    .holdings(holdings)
                    .positions(positions)
                    .todayPnl(extractFundValue(funds, "pnl"))
                    .lastUpdatedAt(java.time.LocalDateTime.now())
                    .dataSource("LIVE")
                    .build());
        } catch (IllegalStateException e) {
            log.warn("Failed to fetch account overview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("Failed to fetch account overview", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch account overview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch account overview", e.getMessage()));
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
        Long userId = resolveUserId(authHeader);
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Fyers account not linked");
        }
        return token;
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization header");
        }
        String jwt = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        if (userId == null) {
            throw new IllegalStateException("Invalid user token");
        }
        return userId;
    }

    @SuppressWarnings("unchecked")
    private String extractProfileName(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object name = dataMap.get("name");
            return name != null ? name.toString() : "Live Account";
        }
        return "Live Account";
    }

    @SuppressWarnings("unchecked")
    private String extractProfileId(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object fyId = dataMap.get("fy_id");
            return fyId != null ? fyId.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private double extractFundValue(Map<String, Object> funds, String key) {
        Object data = funds.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object value = dataMap.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return 0.0;
    }
}
