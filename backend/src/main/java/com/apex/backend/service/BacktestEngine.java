package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.dto.MacdConfirmationDto;
import com.apex.backend.model.BacktestResult;
import com.apex.backend.model.Candle;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.CandleConfirmationValidator;
import com.apex.backend.service.indicator.MacdConfirmationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BacktestEngine {

    private final AtrService atrService;
    private final StrategyProperties strategyProperties;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final MacdConfirmationService macdConfirmationService;
    private final CandleConfirmationValidator candleConfirmationValidator;
    private final BacktestResultRepository backtestResultRepository;
    private final ExecutionCostModel executionCostModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BacktestResult run(String symbol, String timeframe, List<Candle> candles) {
        Map<String, Object> metrics = calculateMetrics(candles);
        String metricsJson = writeJson(metrics);
        LocalDateTime start = candles.isEmpty() ? null : candles.get(0).getTimestamp();
        LocalDateTime end = candles.isEmpty() ? null : candles.get(candles.size() - 1).getTimestamp();
        BacktestResult result = BacktestResult.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .startTime(start)
                .endTime(end)
                .metricsJson(metricsJson)
                .createdAt(LocalDateTime.now())
                .build();
        return backtestResultRepository.save(result);
    }

    public Map<String, Object> calculateMetrics(List<Candle> candles) {
        List<BacktestTrade> trades = simulateTrades(candles);
        return calculateTradeMetrics(trades);
    }

    public double calculateExpectancy(List<Candle> candles) {
        Map<String, Object> metrics = calculateMetrics(candles);
        Object value = metrics.get("expectancyR");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private List<BacktestTrade> simulateTrades(List<Candle> candles) {
        List<BacktestTrade> trades = new ArrayList<>();
        boolean inTrade = false;
        BacktestTrade current = null;
        for (int i = 30; i < candles.size(); i++) {
            List<Candle> window = candles.subList(0, i + 1);
            Candle candle = candles.get(i);
            if (!inTrade) {
                MacdConfirmationDto macd = macdConfirmationService.confirm(window);
                boolean momentum = macd.bullishCrossover() || macd.zeroLineCrossUp();
                boolean candleConfirmed = candleConfirmationValidator.confirm(window).bullishConfirmed();
                if (momentum && candleConfirmed) {
                    double atr = atrService.calculate(window).atr();
                    double entry = candle.getClose();
                    double stop = entry - (atr * strategyProperties.getAtr().getStopMultiplier());
                    double target = entry + (atr * strategyProperties.getAtr().getTargetMultiplier());
                    var entryCost = executionCostModel.estimateExecution(new ExecutionCostModel.ExecutionRequest(
                            "BACKTEST",
                            1,
                            entry,
                            entry,
                            ExecutionCostModel.OrderType.MARKET,
                            ExecutionCostModel.ExecutionSide.BUY,
                            window,
                            null
                    ));
                    current = new BacktestTrade(entryCost.effectivePrice(), stop, target, candle.getTimestamp(), i);
                    inTrade = true;
                }
            } else if (current != null) {
                boolean stopHit = candle.getLow() <= current.stopLoss;
                boolean targetHit = candle.getHigh() >= current.target;
                int barsHeld = i - current.entryIndex;
                if (stopHit || targetHit || barsHeld >= advancedTradingProperties.getBacktest().getMaxBarsInTrade()) {
                    double rawExitPrice = stopHit ? current.stopLoss : targetHit ? current.target : candle.getClose();
                    var exitCost = executionCostModel.estimateExecution(new ExecutionCostModel.ExecutionRequest(
                            "BACKTEST",
                            1,
                            rawExitPrice,
                            rawExitPrice,
                            ExecutionCostModel.OrderType.MARKET,
                            ExecutionCostModel.ExecutionSide.SELL,
                            window,
                            null
                    ));
                    current.exit(exitCost.effectivePrice(), candle.getTimestamp());
                    trades.add(current);
                    inTrade = false;
                    current = null;
                }
            }
        }
        return trades;
    }

    private Map<String, Object> calculateTradeMetrics(List<BacktestTrade> trades) {
        Map<String, Object> metrics = new HashMap<>();
        if (trades.isEmpty()) {
            metrics.put("totalTrades", 0);
            metrics.put("winRate", 0.0);
            return metrics;
        }
        double wins = trades.stream().filter(t -> t.rMultiple > 0).count();
        double winRate = wins / trades.size();
        DoubleSummaryStatistics stats = trades.stream().mapToDouble(t -> t.rMultiple).summaryStatistics();
        double expectancy = stats.getAverage();
        double grossWin = trades.stream().filter(t -> t.rMultiple > 0).mapToDouble(t -> t.rMultiple).sum();
        double grossLoss = trades.stream().filter(t -> t.rMultiple < 0).mapToDouble(t -> Math.abs(t.rMultiple)).sum();
        double profitFactor = grossLoss == 0 ? 0 : grossWin / grossLoss;
        double maxDrawdown = calculateMaxDrawdown(trades);
        double sharpe = calculateSharpe(trades);
        double sortino = calculateSortino(trades);
        Map<String, Long> monthly = new HashMap<>();
        for (BacktestTrade trade : trades) {
            if (trade.exitTime != null) {
                String key = trade.exitTime.getYear() + "-" + trade.exitTime.getMonthValue();
                monthly.put(key, monthly.getOrDefault(key, 0L) + 1);
            }
        }
        metrics.put("totalTrades", trades.size());
        metrics.put("winRate", winRate);
        metrics.put("expectancyR", expectancy);
        metrics.put("profitFactor", profitFactor);
        metrics.put("maxDrawdown", maxDrawdown);
        metrics.put("sharpe", sharpe);
        metrics.put("sortino", sortino);
        metrics.put("rMultipleDistribution", trades.stream().map(t -> t.rMultiple).toList());
        metrics.put("monthlyBreakdown", monthly);
        return metrics;
    }

    private double calculateMaxDrawdown(List<BacktestTrade> trades) {
        double equity = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;
        for (BacktestTrade trade : trades) {
            equity += trade.rMultiple;
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = peak - equity;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private double calculateSharpe(List<BacktestTrade> trades) {
        double mean = trades.stream().mapToDouble(t -> t.rMultiple).average().orElse(0.0);
        double variance = trades.stream().mapToDouble(t -> Math.pow(t.rMultiple - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return stdDev == 0 ? 0 : mean / stdDev;
    }

    private double calculateSortino(List<BacktestTrade> trades) {
        double mean = trades.stream().mapToDouble(t -> t.rMultiple).average().orElse(0.0);
        double downsideVariance = trades.stream()
                .filter(t -> t.rMultiple < 0)
                .mapToDouble(t -> Math.pow(t.rMultiple, 2))
                .average()
                .orElse(0.0);
        double downsideDev = Math.sqrt(downsideVariance);
        return downsideDev == 0 ? 0 : mean / downsideDev;
    }

    private String writeJson(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static class BacktestTrade {
        private final double entry;
        private final double stopLoss;
        private final double target;
        private final LocalDateTime entryTime;
        private final int entryIndex;
        private double exit;
        private LocalDateTime exitTime;
        private double rMultiple;

        private BacktestTrade(double entry, double stopLoss, double target, LocalDateTime entryTime, int entryIndex) {
            this.entry = entry;
            this.stopLoss = stopLoss;
            this.target = target;
            this.entryTime = entryTime;
            this.entryIndex = entryIndex;
        }

        private void exit(double exitPrice, LocalDateTime exitTime) {
            this.exit = exitPrice;
            this.exitTime = exitTime;
            double risk = Math.abs(entry - stopLoss);
            this.rMultiple = risk == 0 ? 0 : (exit - entry) / risk;
        }
    }
}
