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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/performance")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final TradeRepository tradeRepository;

    /**
     * Get comprehensive performance metrics
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
     * Get today's P&L (trades closed today)
     */
    @GetMapping("/today-pnl")
    public ResponseEntity<?> getTodayPnL() {
        try {
            log.info("Fetching today's P&L");
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
            LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

            List<Trade> todayTrades = tradeRepository.findAll().stream()
                .filter(t -> t.getExitTime() != null &&
                        t.getExitTime().isAfter(startOfDay) &&
                        t.getExitTime().isBefore(endOfDay))
                .collect(Collectors.toList());

            double todayPnL = todayTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("todayPnL", todayPnL);
            response.put("tradesCount", todayTrades.size());
            response.put("date", LocalDate.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch today's P&L", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch today's P&L"));
        }
    }

    /**
     * Get unrealized P&L (open positions)
     */
    @GetMapping("/unrealized-pnl")
    public ResponseEntity<?> getUnrealizedPnL() {
        try {
            log.info("Fetching unrealized P&L");
            List<Trade> openTrades = tradeRepository.findAll().stream()
                .filter(t -> t.getStatus() != null && t.getStatus().name().equals("OPEN"))
                .collect(Collectors.toList());

            double unrealizedPnL = openTrades.stream()
                .filter(t -> t.getCurrentStopLoss() != null)
                .mapToDouble(t -> (t.getExitPrice() != null ? t.getExitPrice() : t.getEntryPrice()) * t.getQuantity() - 
                               t.getEntryPrice() * t.getQuantity())
                .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("unrealizedPnL", unrealizedPnL);
            response.put("openPositions", openTrades.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch unrealized P&L", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch unrealized P&L"));
        }
    }

    /**
     * Get win rate metric
     */
    @GetMapping("/win-rate")
    public ResponseEntity<?> getWinRate() {
        try {
            log.info("Fetching win rate");
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
     * Get max drawdown metric
     */
    @GetMapping("/max-drawdown")
    public ResponseEntity<?> getMaxDrawdown() {
        try {
            log.info("Fetching max drawdown");
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
     * Get profit factor metric
     */
    @GetMapping("/profit-factor")
    public ResponseEntity<?> getProfitFactor() {
        try {
            log.info("Fetching profit factor");
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
     * Get Sharpe ratio metric
     */
    @GetMapping("/sharpe-ratio")
    public ResponseEntity<?> getSharpeRatio() {
        try {
            log.info("Fetching Sharpe ratio");
            List<Trade> trades = tradeRepository.findAll();
            double sharpe = performanceService.calculateSharpeRatio(trades);
            return ResponseEntity.ok(new MetricResponse("Sharpe Ratio", sharpe));
        } catch (Exception e) {
            log.error("Failed to calculate Sharpe ratio", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to calculate Sharpe ratio"));
        }
    }

    /**
     * Get ROI metric
     */
    @GetMapping("/roi")
    public ResponseEntity<?> getROI() {
        try {
            log.info("Fetching ROI");
            List<Trade> trades = tradeRepository.findAll();
            double totalPnL = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();
            double roi = performanceService.calculateROI(totalPnL);
            return ResponseEntity.ok(new MetricResponse("ROI", roi));
        } catch (Exception e) {
            log.error("Failed to calculate ROI", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to calculate ROI"));
        }
    }

    /**
     * Get equity curve data
     */
    @GetMapping("/equity-curve")
    public ResponseEntity<?> getEquityCurve(@RequestParam(defaultValue = "PAPER") String type) {
        try {
            log.info("Fetching equity curve for type: {}", type);

            if (!type.equalsIgnoreCase("PAPER") && !type.equalsIgnoreCase("LIVE")) {
                log.warn("Invalid type requested: {}", type);
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Type must be PAPER or LIVE"));
            }

            List<Trade> trades = tradeRepository.findAll().stream()
                .filter(t -> t.isPaperTrade() == type.equalsIgnoreCase("PAPER"))
                .collect(Collectors.toList());

            double[] equityCurve = new double[Math.max(trades.size(), 30)];
            double baseEquity = 100000;
            int idx = 0;

            for (Trade trade : trades) {
                if (trade.getRealizedPnl() != null && idx < equityCurve.length) {
                    baseEquity += trade.getRealizedPnl();
                    equityCurve[idx++] = baseEquity;
                }
            }

            // Fill remaining with last value if trades less than 30
            for (int i = idx; i < equityCurve.length; i++) {
                equityCurve[i] = baseEquity;
            }

            log.info("Equity curve retrieved for {}", type);
            return ResponseEntity.ok(new EquityCurveResponse(type, equityCurve));
        } catch (Exception e) {
            log.error("Failed to fetch equity curve", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch equity curve"));
        }
    }

    // ==================== INNER CLASSES ====================

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

    public static class EquityCurveResponse {
        public String type;
        public double[] curve;

        public EquityCurveResponse(String type, double[] curve) {
            this.type = type;
            this.curve = curve;
        }
    }
}
