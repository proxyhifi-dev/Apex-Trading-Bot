package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.PaperTradeRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.PerformanceService;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final TradeRepository tradeRepository;
    private final PaperTradeRepository paperTradeRepository;
    private final SettingsService settingsService;

    /**
     * Get comprehensive performance metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching performance metrics");
            List<Trade> allTrades = resolveTrades(userId, isPaper);

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
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                .count();
            long lossCount = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0)
                .count();

            double totalPnl = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
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
    public ResponseEntity<?> getTodayPnL(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching today's P&L");
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
            LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

            List<Trade> todayTrades = resolveTrades(userId, isPaper).stream()
                .filter(t -> t.getExitTime() != null &&
                        t.getExitTime().isAfter(startOfDay) &&
                        t.getExitTime().isBefore(endOfDay))
                .collect(Collectors.toList());

            double todayPnL = todayTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
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
    public ResponseEntity<?> getUnrealizedPnL(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching unrealized P&L");
            List<Trade> openTrades = resolveTrades(userId, isPaper).stream()
                .filter(t -> t.getStatus() != null && t.getStatus().name().equals("OPEN"))
                .collect(Collectors.toList());

            double unrealizedPnL = openTrades.stream()
                .filter(t -> t.getCurrentStopLoss() != null)
                .mapToDouble(t -> ((t.getExitPrice() != null ? t.getExitPrice() : t.getEntryPrice()).doubleValue() * t.getQuantity()) -
                               (t.getEntryPrice().doubleValue() * t.getQuantity()))
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
    public ResponseEntity<?> getWinRate(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching win rate");
            List<Trade> trades = resolveTrades(userId, isPaper);
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
    public ResponseEntity<?> getMaxDrawdown(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching max drawdown");
            List<Trade> trades = resolveTrades(userId, isPaper);
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
    public ResponseEntity<?> getProfitFactor(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching profit factor");
            List<Trade> trades = resolveTrades(userId, isPaper);
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
    public ResponseEntity<?> getSharpeRatio(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching Sharpe ratio");
            List<Trade> trades = resolveTrades(userId, isPaper);
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
    public ResponseEntity<?> getROI(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching ROI");
            List<Trade> trades = resolveTrades(userId, isPaper);
            double totalPnL = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
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
    public ResponseEntity<?> getEquityCurve(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            Long userId = requireUserId(principal);
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            log.info("Fetching equity curve for mode: {}", isPaper ? "PAPER" : "LIVE");

            List<Trade> trades = resolveTrades(userId, isPaper);

            double[] equityCurve = new double[Math.max(trades.size(), 30)];
            double baseEquity = 100000;
            int idx = 0;

            for (Trade trade : trades) {
                if (trade.getRealizedPnl() != null && idx < equityCurve.length) {
                    baseEquity += trade.getRealizedPnl().doubleValue();
                    equityCurve[idx++] = baseEquity;
                }
            }

            // Fill remaining with last value if trades less than 30
            for (int i = idx; i < equityCurve.length; i++) {
                equityCurve[i] = baseEquity;
            }

            String modeLabel = isPaper ? "PAPER" : "LIVE";
            log.info("Equity curve retrieved for {}", modeLabel);
            return ResponseEntity.ok(new EquityCurveResponse(modeLabel, equityCurve));
        } catch (Exception e) {
            log.error("Failed to fetch equity curve", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch equity curve"));
        }
    }

    private List<Trade> resolveTrades(Long userId, boolean isPaper) {
        if (isPaper) {
            return paperTradeRepository.findByUserId(userId).stream()
                    .map(this::mapPaperTrade)
                    .collect(Collectors.toList());
        }
        return tradeRepository.findByUserIdAndIsPaperTrade(userId, false);
    }

    private Trade mapPaperTrade(PaperTrade paperTrade) {
        Trade.TradeStatus status = "OPEN".equalsIgnoreCase(paperTrade.getStatus())
                ? Trade.TradeStatus.OPEN
                : Trade.TradeStatus.CLOSED;
        Trade.TradeType tradeType = "SELL".equalsIgnoreCase(paperTrade.getSide())
                ? Trade.TradeType.SHORT
                : Trade.TradeType.LONG;
        return Trade.builder()
                .userId(paperTrade.getUserId())
                .symbol(paperTrade.getSymbol())
                .quantity(paperTrade.getQuantity())
                .entryPrice(paperTrade.getEntryPrice())
                .exitPrice(paperTrade.getExitPrice())
                .entryTime(paperTrade.getEntryTime())
                .exitTime(paperTrade.getExitTime())
                .realizedPnl(paperTrade.getRealizedPnl())
                .status(status)
                .tradeType(tradeType)
                .build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
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
