package com.apex.backend.controller;

import com.apex.backend.dto.SignalDTO;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BotScheduler;
import com.apex.backend.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
@Tag(name = "Signals")
public class SignalsController {

    private final BotScheduler botScheduler;
    private final StockScreeningResultRepository screeningRepository;
    private final WatchlistService watchlistService;

    @PostMapping("/scan-now")
    @Operation(summary = "Trigger a scan to generate signals")
    public ResponseEntity<?> scanNow(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        if (watchlistService.isWatchlistEmpty(userId)) {
            return ResponseEntity.ok(new MessageResponse("Watchlist empty"));
        }
        new Thread(() -> botScheduler.forceScan(userId)).start();
        return ResponseEntity.ok(new MessageResponse("Scan triggered"));
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent signals for the authenticated user")
    public ResponseEntity<?> recentSignals(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        List<SignalDTO> signals = screeningRepository.findTop50ByUserIdOrderByScanTimeDesc(userId)
                .stream()
                .map(result -> SignalDTO.builder()
                        .id(result.getId())
                        .symbol(result.getSymbol())
                        .signalScore(result.getSignalScore())
                        .grade(result.getGrade())
                        .entryPrice(resolveEntryPrice(result.getEntryPrice(), result.getCurrentPrice()))
                        .scanTime(result.getScanTime())
                        .hasEntrySignal(true)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(signals);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    public static class MessageResponse {
        public String message;
        public long timestamp;

        public MessageResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private BigDecimal resolveEntryPrice(BigDecimal entryPrice, BigDecimal currentPrice) {
        if (entryPrice != null && entryPrice.compareTo(BigDecimal.ZERO) > 0) {
            return entryPrice;
        }
        return currentPrice;
    }
}
