package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.PerformanceService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeRepository tradeRepository;
    private final PerformanceService performanceService;
    private final SettingsService settingsService;

    /**
     * Get all trade history
     */
    @GetMapping("/history")
    public ResponseEntity<?> getTradeHistory(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching trade history");
            List<Trade> trades = tradeRepository.findByUserIdAndIsPaperTrade(userId, isPaper);
            log.info("Retrieved {} trades from database", trades.size());
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            log.error("Failed to fetch trade history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch trade history"));
        }
    }

    /**
     * Get performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<?> getPerformance(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Calculating performance metrics");
            List<Trade> allTrades = tradeRepository.findByUserIdAndIsPaperTrade(userId, isPaper);

            if (allTrades.isEmpty()) {
                log.info("No trades found, returning empty metrics");
                return ResponseEntity.ok(PerformanceMetrics.builder()
                        .totalTrades(0)
                        .winRate(0)
                        .netProfit(0)
                        .profitFactor(0)
                        .maxDrawdown(0)
                        .build());
            }

            long winCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                    .count();

            long lossCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0)
                    .count();

            double totalPnl = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null)
                    .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                    .sum();

            double grossWin = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                    .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                    .sum();

            double grossLoss = Math.abs(allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0)
                    .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                    .sum());

            double profitFactor = calculateProfitFactor(grossWin, grossLoss);
            double maxDrawdown = performanceService.calculateMaxDrawdown(allTrades);
            double winRate = allTrades.isEmpty() ? 0 : (double) winCount / allTrades.size() * 100;

            PerformanceMetrics metrics = PerformanceMetrics.builder()
                    .totalTrades(allTrades.size())
                    .winningTrades((int) winCount)
                    .losingTrades((int) lossCount)
                    .winRate(winRate)
                    .netProfit(totalPnl)
                    .averageWin(winCount > 0 ? grossWin / winCount : 0)
                    .averageLoss(lossCount > 0 ? -grossLoss / lossCount : 0)
                    .profitFactor(profitFactor)
                    .expectancy(performanceService.calculateExpectancy(allTrades))
                    .maxDrawdown(maxDrawdown)
                    .longestWinStreak(performanceService.calculateLongestWinStreak(allTrades))
                    .longestLossStreak(performanceService.calculateLongestLossStreak(allTrades))
                    .build();

            log.info("Performance metrics calculated: Trades={}, WinRate={}%, MaxDD={}%",
                    metrics.getTotalTrades(), metrics.getWinRate(), metrics.getMaxDrawdown());

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Failed to calculate performance metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate performance metrics"));
        }
    }

    /**
     * Get trades by symbol
     */
    @GetMapping("/by-symbol")
    public ResponseEntity<?> getTradesBySymbol(@RequestParam String symbol,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching trades for symbol: {}", symbol);
            List<Trade> trades = tradeRepository.findByUserIdAndSymbolAndIsPaperTradeOrderByEntryTimeAsc(
                    userId, symbol, isPaper);
            log.info("Retrieved {} trades for symbol: {}", trades.size(), symbol);
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            log.error("Failed to fetch trades for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch trades for symbol: " + symbol));
        }
    }

    /**
     * Get open trades only
     */
    @GetMapping("/open")
    public ResponseEntity<?> getOpenTrades(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching open trades");
            List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(
                    userId, isPaper, Trade.TradeStatus.OPEN);
            log.info("Retrieved {} open trades", openTrades.size());
            return ResponseEntity.ok(openTrades);
        } catch (Exception e) {
            log.error("Failed to fetch open trades", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch open trades"));
        }
    }

    /**
     * Get closed trades only
     */
    @GetMapping("/closed")
    public ResponseEntity<?> getClosedTrades(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching closed trades");
            List<Trade> closedTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(
                    userId, isPaper, Trade.TradeStatus.CLOSED);
            log.info("Retrieved {} closed trades", closedTrades.size());
            return ResponseEntity.ok(closedTrades);
        } catch (Exception e) {
            log.error("Failed to fetch closed trades", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch closed trades"));
        }
    }

    /**
     * Calculate profit factor safely
     */
    private double calculateProfitFactor(double grossWin, double grossLoss) {
        if (grossLoss == 0) {
            return grossWin > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return grossWin / grossLoss;
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    // Error response wrapper
    public static class ErrorResponse {
        public String error;
        public long timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
