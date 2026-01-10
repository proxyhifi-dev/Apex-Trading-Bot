package com.apex.backend.service;

import com.apex.backend.config.AnalyticsProperties;
import com.apex.backend.model.BacktestResult;
import com.apex.backend.model.ValidationRun;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.repository.ValidationRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class BacktestValidationService {

    private final BacktestResultRepository backtestResultRepository;
    private final ValidationRunRepository validationRunRepository;
    private final DeflatedSharpeCalculator deflatedSharpeCalculator;
    private final AnalyticsProperties analyticsProperties;
    private final CvarService cvarService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ValidationRun validate(Long userId, Long backtestResultId) {
        BacktestResult result = backtestResultRepository.findByIdAndUserId(backtestResultId, userId).orElseThrow();
        List<Double> returns = extractReturns(result.getMetricsJson());
        Map<String, Object> metrics = new HashMap<>();
        if (returns.isEmpty()) {
            metrics.put("message", "No trades to validate");
        } else {
            metrics.putAll(runBootstrap(returns, result.getStartTime(), result.getEndTime()));
            double sharpe = (double) metrics.getOrDefault("sharpe", 0.0);
            double deflated = deflatedSharpeCalculator.calculate(sharpe, returns.size(), analyticsProperties.getValidation().getNumTrials());
            metrics.put("deflatedSharpe", deflated);
            double cvar = cvarService.calculate(returns, analyticsProperties.getCvar().getConfidence() / 100.0);
            metrics.put("cvar", cvar);
        }
        String json = writeJson(metrics);
        ValidationRun run = ValidationRun.builder()
                .userId(userId)
                .backtestResultId(backtestResultId)
                .metricsJson(json)
                .createdAt(LocalDateTime.now())
                .build();
        return validationRunRepository.save(run);
    }

    private List<Double> extractReturns(String metricsJson) {
        try {
            Map<String, Object> metrics = objectMapper.readValue(metricsJson, new TypeReference<>() {});
            Object dist = metrics.get("rMultipleDistribution");
            if (dist instanceof List<?> list) {
                List<Double> values = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Number num) {
                        values.add(num.doubleValue());
                    }
                }
                return values;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private Map<String, Object> runBootstrap(List<Double> returns, LocalDateTime start, LocalDateTime end) {
        int samples = analyticsProperties.getValidation().getBootstrapSamples();
        List<Double> cagrDist = new ArrayList<>();
        List<Double> maxDdDist = new ArrayList<>();
        List<Double> profitFactorDist = new ArrayList<>();
        List<Double> sharpeDist = new ArrayList<>();
        Random random = new Random(42);

        for (int i = 0; i < samples; i++) {
            List<Double> sample = new ArrayList<>();
            for (int j = 0; j < returns.size(); j++) {
                sample.add(returns.get(random.nextInt(returns.size())));
            }
            cagrDist.add(calculateCagr(sample, start, end));
            maxDdDist.add(calculateMaxDrawdown(sample));
            profitFactorDist.add(calculateProfitFactor(sample));
            sharpeDist.add(calculateSharpe(sample));
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cagr", average(cagrDist));
        metrics.put("maxDrawdown", average(maxDdDist));
        metrics.put("profitFactor", average(profitFactorDist));
        metrics.put("sharpe", average(sharpeDist));
        metrics.put("bootstrapSamples", samples);
        return metrics;
    }

    private double calculateCagr(List<Double> returns, LocalDateTime start, LocalDateTime end) {
        double equity = 1.0;
        for (double value : returns) {
            equity *= (1.0 + value);
        }
        double years = 1.0;
        if (start != null && end != null) {
            long days = Duration.between(start, end).toDays();
            years = Math.max(days / 365.0, 1.0 / 365.0);
        }
        return Math.pow(equity, 1.0 / years) - 1.0;
    }

    private double calculateMaxDrawdown(List<Double> returns) {
        double equity = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;
        for (double value : returns) {
            equity += value;
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

    private double calculateProfitFactor(List<Double> returns) {
        double grossWin = returns.stream().filter(v -> v > 0).mapToDouble(Double::doubleValue).sum();
        double grossLoss = returns.stream().filter(v -> v < 0).mapToDouble(v -> Math.abs(v)).sum();
        return grossLoss == 0 ? 0.0 : grossWin / grossLoss;
    }

    private double calculateSharpe(List<Double> returns) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return stdDev == 0 ? 0.0 : mean / stdDev;
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String writeJson(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            return "{}";
        }
    }
}
