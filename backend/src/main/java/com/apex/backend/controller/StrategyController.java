package com.apex.backend.controller;

import com.apex.backend.dto.SignalDTO;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BotScheduler;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    
    /**
     * Trigger manual market scan
     */
    @PostMapping("/scan-now")
    public ResponseEntity<?> manualScan(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Manual scan triggered by user");
        Long userId = requireUserId(principal);
        new Thread(() -> botScheduler.forceScan(userId)).start();
        return ResponseEntity.ok(new MessageResponse("Market Scan Triggered!"));
    }
    
    /**
     * Get all trading signals
     */
    @GetMapping("/signals")
    public ResponseEntity<?> getAllSignals(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        log.info("Fetching all trading signals");
        List<SignalDTO> signals = screeningRepo.findTop50ByUserIdOrderByScanTimeDesc(userId)
                .stream()
                .map(s -> SignalDTO.builder()
                        .id(s.getId())
                        .symbol(s.getSymbol())
                        .signalScore(s.getSignalScore())
                        .grade(s.getGrade())
                        .entryPrice(resolveEntryPrice(s))
                        .scanTime(s.getScanTime())
                        .hasEntrySignal(true)
                        .build())
                .collect(Collectors.toList());
        log.info("Retrieved {} signals", signals.size());
        return ResponseEntity.ok(signals);
    }
    
    /**
     * Get pending approval signals
     */
    @GetMapping("/signals/pending")
    public ResponseEntity<?> getPendingSignals(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        log.info("Fetching pending signals");
        List<SignalDTO> signals = screeningRepo.findByUserIdAndApprovalStatus(
                        userId, StockScreeningResult.ApprovalStatus.PENDING)
                .stream()
                .map(s -> SignalDTO.builder()
                        .id(s.getId())
                        .symbol(s.getSymbol())
                        .entryPrice(s.getEntryPrice())
                        .build())
                .collect(Collectors.toList());
        log.info("Retrieved {} pending signals", signals.size());
        return ResponseEntity.ok(signals);
    }
    
    /**
     * Toggle paper/live trading mode
     */
    @PostMapping("/mode")
    public ResponseEntity<?> toggleMode(@RequestParam boolean paperMode,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling mode to paperMode={}", paperMode);
        Long userId = requireUserId(principal);
        settingsService.updateSettings(userId, com.apex.backend.dto.SettingsDTO.builder()
                .mode(paperMode ? "paper" : "live")
                .build());
        return ResponseEntity.ok(Map.of("paperMode", paperMode, "message",
                paperMode ? "Switched to Paper Trading" : "Switched to Live Trading"));
    }
    
    /**
     * Get current trading mode
     */
    @GetMapping("/mode")
    public ResponseEntity<?> getMode(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching current trading mode");
        Long userId = requireUserId(principal);
        boolean paperMode = "paper".equalsIgnoreCase(settingsService.getModeForUser(userId));
        return ResponseEntity.ok(Map.of("paperMode", paperMode));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private BigDecimal resolveEntryPrice(StockScreeningResult result) {
        if (result.getEntryPrice() != null && result.getEntryPrice().compareTo(BigDecimal.ZERO) > 0) {
            return result.getEntryPrice();
        }
        return result.getCurrentPrice();
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
    
}
