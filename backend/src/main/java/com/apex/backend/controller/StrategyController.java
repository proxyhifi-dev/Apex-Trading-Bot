package com.apex.backend.controller;

import com.apex.backend.dto.SignalBulkApproveRequest;
import com.apex.backend.dto.SignalDTO;
import com.apex.backend.dto.SignalDecisionRequest;
import com.apex.backend.dto.SignalDetailDTO;
import com.apex.backend.dto.StrategyHealthResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.TradingMode;
import com.apex.backend.dto.TradingModeResponse;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BotScheduler;
import com.apex.backend.service.BroadcastService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.StrategyHealthService;
import com.apex.backend.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
@Tag(name = "Strategy")
public class StrategyController {
    
    private final StockScreeningResultRepository screeningRepo;
    private final TradeExecutionService tradeExecutionService;
    private final BotScheduler botScheduler;
    private final SettingsService settingsService;
    private final StrategyHealthService strategyHealthService;
    private final BroadcastService broadcastService;
    
    /**
     * Trigger manual market scan
     */
    @PostMapping("/scan-now")
    public ResponseEntity<?> manualScan(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Manual scan triggered by user");
        requireUserId(principal);
        new Thread(botScheduler::forceScan).start();
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

    @GetMapping("/signals/{signalId}")
    public ResponseEntity<SignalDetailDTO> getSignal(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long signalId) {
        Long userId = requireUserId(principal);
        StockScreeningResult signal = loadSignal(signalId, userId);
        return ResponseEntity.ok(toDetailDto(signal));
    }

    @PostMapping("/signals/{signalId}/approve")
    public ResponseEntity<SignalDetailDTO> approveSignal(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long signalId,
                                                         @RequestBody(required = false) @jakarta.validation.Valid SignalDecisionRequest request) {
        Long userId = requireUserId(principal);
        StockScreeningResult signal = loadSignal(signalId, userId);
        boolean override = request != null && request.isOverride();
        if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.REJECTED && !override) {
            throw new ConflictException("Rejected signals require override");
        }
        if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.REJECTED && override && !isAdmin(principal)) {
            throw new org.springframework.security.access.AccessDeniedException("Admin override required");
        }
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.APPROVED);
        signal.setDecisionReason(request != null ? request.getReason() : null);
        signal.setDecisionNotes(request != null ? request.getNotes() : null);
        signal.setDecidedBy(principal.getUsername());
        signal.setDecisionAt(java.time.LocalDateTime.now());
        screeningRepo.save(signal);
        broadcastService.broadcastSignal(userId, toDetailDto(signal));
        return ResponseEntity.ok(toDetailDto(signal));
    }

    @PostMapping("/signals/{signalId}/reject")
    public ResponseEntity<SignalDetailDTO> rejectSignal(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long signalId,
                                                        @RequestBody @jakarta.validation.Valid SignalDecisionRequest request) {
        Long userId = requireUserId(principal);
        StockScreeningResult signal = loadSignal(signalId, userId);
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
        signal.setDecisionReason(request.getReason());
        signal.setDecisionNotes(request.getNotes());
        signal.setDecidedBy(principal.getUsername());
        signal.setDecisionAt(java.time.LocalDateTime.now());
        screeningRepo.save(signal);
        broadcastService.broadcastSignal(userId, toDetailDto(signal));
        return ResponseEntity.ok(toDetailDto(signal));
    }

    @PostMapping("/signals/bulk-approve")
    public ResponseEntity<List<SignalDetailDTO>> bulkApprove(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestBody @jakarta.validation.Valid SignalBulkApproveRequest request) {
        Long userId = requireUserId(principal);
        List<SignalDetailDTO> approved = request.getIds().stream()
                .map(id -> loadSignal(id, userId))
                .map(signal -> {
                    if (signal.getApprovalStatus() != StockScreeningResult.ApprovalStatus.REJECTED) {
                        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.APPROVED);
                        signal.setDecidedBy(principal.getUsername());
                        signal.setDecisionAt(java.time.LocalDateTime.now());
                        screeningRepo.save(signal);
                        broadcastService.broadcastSignal(userId, toDetailDto(signal));
                    }
                    return toDetailDto(signal);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(approved);
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
    public ResponseEntity<?> toggleMode(@RequestParam String mode,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling mode to {}", mode);
        Long userId = requireUserId(principal);
        TradingMode tradingMode;
        try {
            tradingMode = TradingMode.fromRequest(mode);
        } catch (IllegalArgumentException ex) {
            throw new com.apex.backend.exception.BadRequestException("Mode must be PAPER or LIVE");
        }
        settingsService.updateSettings(userId, com.apex.backend.dto.SettingsDTO.builder()
                .mode(tradingMode.name())
                .build());
        return ResponseEntity.ok(TradingModeResponse.builder()
                .mode(tradingMode.name())
                .build());
    }
    
    /**
     * Get current trading mode
     */
    @GetMapping("/mode")
    public ResponseEntity<?> getMode(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching current trading mode");
        Long userId = requireUserId(principal);
        TradingMode tradingMode = settingsService.getTradingMode(userId);
        return ResponseEntity.ok(TradingModeResponse.builder()
                .mode(tradingMode.name())
                .build());
    }

    @GetMapping("/health")
    public ResponseEntity<StrategyHealthResponse> getHealth(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        var decision = strategyHealthService.evaluate(userId);
        var state = strategyHealthService.getLatestState(userId);
        return ResponseEntity.ok(new StrategyHealthResponse(
                decision.status().name(),
                state != null && state.isPaused(),
                decision.reasons()
        ));
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(@AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal);
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(strategyHealthService.pause(userId, "Manual pause"));
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(@AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal);
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(strategyHealthService.resume(userId));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private void requireRole(UserPrincipal principal) {
        if (principal == null || principal.getRole() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        String role = principal.getRole();
        if (!role.equalsIgnoreCase("ADMIN") && !role.equalsIgnoreCase("USER")) {
            throw new UnauthorizedException("Insufficient permissions");
        }
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getRole() != null && principal.getRole().equalsIgnoreCase("ADMIN");
    }

    private SignalDetailDTO toDetailDto(StockScreeningResult signal) {
        return SignalDetailDTO.builder()
                .id(signal.getId())
                .symbol(signal.getSymbol())
                .signalScore(signal.getSignalScore())
                .grade(signal.getGrade())
                .entryPrice(signal.getEntryPrice())
                .stopLoss(signal.getStopLoss())
                .scanTime(signal.getScanTime())
                .approvalStatus(signal.getApprovalStatus().name())
                .analysisReason(signal.getAnalysisReason())
                .scoreBreakdown(signal.getScoreBreakdown())
                .decisionReason(signal.getDecisionReason())
                .decisionNotes(signal.getDecisionNotes())
                .decidedBy(signal.getDecidedBy())
                .decisionAt(signal.getDecisionAt())
                .build();
    }

    private StockScreeningResult loadSignal(Long signalId, Long userId) {
        StockScreeningResult signal = screeningRepo.findById(signalId)
                .orElseThrow(() -> new NotFoundException("Signal not found"));
        if (!signal.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        return signal;
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
