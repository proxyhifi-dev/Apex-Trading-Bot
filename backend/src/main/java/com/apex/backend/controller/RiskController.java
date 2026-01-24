package com.apex.backend.controller;

import com.apex.backend.dto.RiskEventResponse;
import com.apex.backend.dto.RiskLimitsResponse;
import com.apex.backend.dto.RiskLimitsUpdateRequest;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.CorrelationService;
import com.apex.backend.service.EmergencyStopService;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.RiskLimitService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.repository.RiskEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Tag(name = "Risk")
public class RiskController {
    
    private final PortfolioService portfolioService;
    private final CorrelationService correlationService;
    private final EmergencyStopService emergencyStopService;
    private final SettingsService settingsService;
    private final RiskLimitService riskLimitService;
    private final RiskEventRepository riskEventRepository;
    
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

    @GetMapping("/limits")
    public ResponseEntity<RiskLimitsResponse> getLimits(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(riskLimitService.getLimits(userId));
    }

    @PutMapping("/limits")
    public ResponseEntity<RiskLimitsResponse> updateLimits(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody @jakarta.validation.Valid RiskLimitsUpdateRequest request) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(riskLimitService.updateLimits(userId, request));
    }

    @GetMapping("/events")
    public ResponseEntity<List<RiskEventResponse>> getRiskEvents(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestParam(required = false) String from,
                                                                 @RequestParam(required = false) String to,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) String type) {
        Long userId = requireUserId(principal);
        java.time.Instant fromInstant = from != null ? java.time.Instant.parse(from) : java.time.Instant.now().minusSeconds(86400 * 30L);
        java.time.Instant toInstant = to != null ? java.time.Instant.parse(to) : java.time.Instant.now();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var results = (type == null || type.isBlank())
                ? riskEventRepository.findByUserIdAndCreatedAtBetween(userId, fromInstant, toInstant, pageable)
                : riskEventRepository.findByUserIdAndTypeAndCreatedAtBetween(userId, type, fromInstant, toInstant, pageable);
        List<RiskEventResponse> response = results.stream()
                .map(event -> RiskEventResponse.builder()
                        .id(event.getId())
                        .type(event.getType())
                        .description(event.getDescription())
                        .metadata(event.getMetadata())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Trigger emergency stop
     * Halts all trading operations immediately
     */
    @PostMapping("/emergency-stop")
    public ResponseEntity<?> triggerEmergencyStop(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            requireAdmin(principal);
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

    private void requireAdmin(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        String role = principal.getRole();
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        if (!"ADMIN".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
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
