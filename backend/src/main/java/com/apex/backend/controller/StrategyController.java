package com.apex.backend.controller;

import com.apex.backend.dto.SignalDTO;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.BotScheduler;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyController {
    
    private final StockScreeningResultRepository screeningRepo;
    private final TradeExecutionService tradeExecutionService;
    private final BotScheduler botScheduler;
    private final SettingsService settingsService;
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * Trigger manual market scan
     */
    @PostMapping("/scan-now")
    public ResponseEntity<?> manualScan() {
        try {
            log.info("Manual scan triggered by user");
            new Thread(botScheduler::forceScan).start();
            return ResponseEntity.ok(new MessageResponse("Market Scan Triggered!"));
        } catch (Exception e) {
            log.error("Failed to trigger manual scan", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to trigger scan"));
        }
    }
    
    /**
     * Get all trading signals
     */
    @GetMapping("/signals")
    public ResponseEntity<?> getAllSignals() {
        try {
            log.info("Fetching all trading signals");
            List<SignalDTO> signals = screeningRepo.findAll()
                    .stream()
                    .map(s -> SignalDTO.builder()
                            .id(s.getId())
                            .symbol(s.getSymbol())
                            .signalScore(s.getSignalScore())
                            .grade(s.getGrade())
                            .entryPrice(s.getEntryPrice() > 0 ? s.getEntryPrice() : s.getCurrentPrice())
                            .scanTime(s.getScanTime())
                            .hasEntrySignal(true)
                            .build())
                    .collect(Collectors.toList());
            log.info("Retrieved {} signals", signals.size());
            return ResponseEntity.ok(signals);
        } catch (Exception e) {
            log.error("Failed to fetch signals", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch signals"));
        }
    }
    
    /**
     * Get pending approval signals
     */
    @GetMapping("/signals/pending")
    public ResponseEntity<?> getPendingSignals() {
        try {
            log.info("Fetching pending signals");
            List<SignalDTO> signals = screeningRepo.findByApprovalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .stream()
                    .map(s -> SignalDTO.builder()
                            .id(s.getId())
                            .symbol(s.getSymbol())
                            .entryPrice(s.getEntryPrice())
                            .build())
                    .collect(Collectors.toList());
            log.info("Retrieved {} pending signals", signals.size());
            return ResponseEntity.ok(signals);
        } catch (Exception e) {
            log.error("Failed to fetch pending signals", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch pending signals"));
        }
    }
    
    /**
     * Toggle paper/live trading mode
     */
    @PostMapping("/mode")
    public ResponseEntity<?> toggleMode(@RequestParam boolean paperMode,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Toggling mode to paperMode={}", paperMode);
            Long userId = resolveUserId(authHeader);
            settingsService.updateSettings(userId, com.apex.backend.dto.SettingsDTO.builder()
                    .mode(paperMode ? "paper" : "live")
                    .build());
            return ResponseEntity.ok(Map.of("paperMode", paperMode, "message", 
                    paperMode ? "Switched to Paper Trading" : "Switched to Live Trading"));
        } catch (Exception e) {
            log.error("Failed to toggle mode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to toggle mode"));
        }
    }
    
    /**
     * Get current trading mode
     */
    @GetMapping("/mode")
    public ResponseEntity<?> getMode(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching current trading mode");
            Long userId = resolveUserId(authHeader);
            boolean paperMode = "paper".equalsIgnoreCase(settingsService.getModeForUser(userId));
            return ResponseEntity.ok(Map.of("paperMode", paperMode));
        } catch (Exception e) {
            log.error("Failed to fetch trading mode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch trading mode"));
        }
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
    
    // ==================== INNER CLASSES ====================
    
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
