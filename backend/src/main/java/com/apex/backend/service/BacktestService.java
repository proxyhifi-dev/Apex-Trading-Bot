package com.apex.backend.service;

import com.apex.backend.model.BacktestResult;
import com.apex.backend.model.Candle;
import com.apex.backend.repository.BacktestResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BacktestService {

    private final FyersService fyersService;
    private final BacktestEngine backtestEngine;
    private final WalkForwardValidationService walkForwardValidationService;
    private final BacktestResultRepository backtestResultRepository;
    private final DataAdjustmentService dataAdjustmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BacktestResult runBacktest(Long userId, String symbol, String timeframe, int bars) {
        List<Candle> candles = fyersService.getHistoricalData(symbol, bars, timeframe);
        candles = dataAdjustmentService.applyCorporateActions(candles, List.of());
        Map<String, Object> metrics = new HashMap<>(backtestEngine.calculateMetrics(candles));
        metrics.put("walkForward", walkForwardValidationService.validate(symbol, timeframe, candles));
        metrics.put("biasNotes", List.of(
                "Survivorship bias: ensure universe includes delisted symbols for full analysis.",
                "Look-ahead bias guardrail: signals computed using data up to current bar only.",
                "Corporate actions adjustments applied when actions are provided."
        ));
        String metricsJson = toJson(metrics);
        BacktestResult result = BacktestResult.builder()
                .userId(userId)
                .symbol(symbol)
                .timeframe(timeframe)
                .startTime(candles.isEmpty() ? null : candles.get(0).getTimestamp())
                .endTime(candles.isEmpty() ? null : candles.get(candles.size() - 1).getTimestamp())
                .metricsJson(metricsJson)
                .createdAt(LocalDateTime.now())
                .build();
        return backtestResultRepository.save(result);
    }

    private String toJson(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
