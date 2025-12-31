package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeRepository tradeRepository;
    private final PerformanceService performanceService;

    /**
     * Get all trade history
     * FIXED: Added error handling and logging
     */
    @GetMapping("/history")
    public ResponseEntity<?> getTradeHistory() {
        try {
            log.info("Fetching trade history");
            List<Trade> trades = tradeRepository.findAll();
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
     * FIXED: Proper max drawdown calculation, null checks, error handling
     */
    @GetMapping("/performance")
    public ResponseEntity<?> getPerformance() {
        try {
            log.info("Calculating performance metrics");
            List<Trade> allTrades = tradeRepository.findAll();

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

            // ✅ FIXED: Proper null checking
            long winCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                    .count();

            long lossCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                    .count();

            // ✅ FIXED: Proper null handling in sum
            double totalPnl = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null)
                    .mapToDouble(Trade::getRealizedPnl)
                    .sum();

            double grossWin = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                    .mapToDouble(Trade::getRealizedPnl)
                    .sum();

            double grossLoss = Math.abs(allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                    .mapToDouble(Trade::getRealizedPnl)
                    .sum());

            // ✅ FIXED: Proper division by zero handling
            double profitFactor = calculateProfitFactor(grossWin, grossLoss);

            // ✅ FIXED: Proper max drawdown calculation
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
                    .maxDrawdown(maxDrawdown)
                    .build();

            log.info("Performance metrics calculated successfully - Total Trades: {}, Win Rate: {}%, Max Drawdown: {}%",
                    metrics.getTotalTrades(), metrics.getWinRate(), metrics.getMaxDrawdown());

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Failed to calculate performance metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate performance metrics"));
        }
    }

    /**
     * FIXED: Proper profit factor calculation with zero division handling
     */
    private double calculateProfitFactor(double grossWin, double grossLoss) {
        if (grossLoss == 0) {
            return grossWin > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return grossWin / grossLoss;
    }

    /**
     * Get trades for a specific symbol
     * FIXED: New endpoint for detailed analysis
     */
    @GetMapping("/by-symbol")
    public ResponseEntity<?> getTradesBySymbol(@RequestParam String symbol) {
        try {
            log.info("Fetching trades for symbol: {}", symbol);
            List<Trade> trades = tradeRepository.findBySymbol(symbol);
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
     * FIXED: New endpoint to monitor active positions
     */
    @GetMapping("/open")
    public ResponseEntity<?> getOpenTrades() {
        try {
            log.info("Fetching open trades");
            List<Trade> openTrades = tradeRepository.findByStatus("OPEN");
            log.info("Retrieved {} open trades", openTrades.size());
            return ResponseEntity.ok(openTrades);
        } catch (Exception e) {
            log.error("Failed to fetch open trades", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch open trades"));
        }
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
