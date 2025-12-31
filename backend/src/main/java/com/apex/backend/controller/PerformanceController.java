package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final TradeRepository tradeRepository;

    /**
     * Get performance metrics
     * FIXED: No longer calls non-existent calculateMetrics() method
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        try {
            log.info("Fetching performance metrics");
            List<Trade> allTrades = tradeRepository.findAll();

            if (allTrades.isEmpty()) {
                return ResponseEntity.ok(PerformanceMetrics.builder()
                        .totalTrades(0)
                        .winRate(0)
                        .netProfit(0)
                        .profitFactor(0)
                        .maxDrawdown(0)
                        .build());
            }

            long winCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                    .count();

            long lossCount = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                    .count();

            double totalPnl = allTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null)
                    .mapToDouble(Trade::getRealizedPnl)
                    .sum();

            PerformanceMetrics metrics = PerformanceMetrics.builder()
                    .totalTrades(allTrades.size())
                    .winningTrades((int) winCount)
                    .losingTrades((int) lossCount)
                    .winRate(performanceService.calculateWinRate(allTrades))
                    .netProfit(totalPnl)
                    .profitFactor(performanceService.calculateProfitFactor(allTrades))
                    .expectancy(performanceService.calculateExpectancy(allTrades))
                    .maxDrawdown(performanceService.calculateMaxDrawdown(allTrades))
                    .longestWinStreak(performanceService.calculateLongestWinStreak(allTrades))
                    .longestLossStreak(performanceService.calculateLongestLossStreak(allTrades))
                    .build();

            log.info("Performance metrics retrieved successfully");
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Failed to fetch performance metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch metrics"));
        }
    }

    /**
     * Get win rate only
     */
    @GetMapping("/win-rate")
    public ResponseEntity<?> getWinRate() {
        try {
            List<Trade> trades = tradeRepository.findAll();
            double winRate = performanceService.calculateWinRate(trades);
            return ResponseEntity.ok(new MetricResponse("Win Rate", winRate));
        } catch (Exception e) {
            log.error("Failed to calculate win rate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate win rate"));
        }
    }

    /**
     * Get max drawdown only
     */
    @GetMapping("/max-drawdown")
    public ResponseEntity<?> getMaxDrawdown() {
        try {
            List<Trade> trades = tradeRepository.findAll();
            double maxDD = performanceService.calculateMaxDrawdown(trades);
            return ResponseEntity.ok(new MetricResponse("Max Drawdown", maxDD));
        } catch (Exception e) {
            log.error("Failed to calculate max drawdown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate max drawdown"));
        }
    }

    /**
     * Get profit factor only
     */
    @GetMapping("/profit-factor")
    public ResponseEntity<?> getProfitFactor() {
        try {
            List<Trade> trades = tradeRepository.findAll();
            double pf = performanceService.calculateProfitFactor(trades);
            return ResponseEntity.ok(new MetricResponse("Profit Factor", pf));
        } catch (Exception e) {
            log.error("Failed to calculate profit factor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate profit factor"));
        }
    }

    /**
     * Get Sharpe Ratio
     */
    @GetMapping("/sharpe-ratio")
    public ResponseEntity<?> getSharpeRatio() {
        try {
            List<Trade> trades = tradeRepository.findAll();
            double sharpe = performanceService.calculateSharpeRatio(trades);
            return ResponseEntity.ok(new MetricResponse("Sharpe Ratio", sharpe));
        } catch (Exception e) {
            log.error("Failed to calculate Sharpe ratio", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to calculate Sharpe ratio"));
        }
    }

    // Response wrappers
    public static class ErrorResponse {
        public String error;
        public long timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class MetricResponse {
        public String metric;
        public double value;

        public MetricResponse(String metric, double value) {
            this.metric = metric;
            this.value = value;
        }
    }
}
