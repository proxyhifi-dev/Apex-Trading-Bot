package com.apex.backend.service;

import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStrategy {

    private final IndicatorEngine indicatorEngine;

    public void runSampleBacktest(List<Candle> history) {
        if (history == null || history.size() < 50) {
            log.warn("Not enough data for backtest");
            return;
        }

        IndicatorEngine.MacdResult macd = indicatorEngine.calculateMACD(history);
        IndicatorEngine.AdxResult adx = indicatorEngine.calculateADX(history);
        double rsi = indicatorEngine.calculateRSI(history);

        log.info("Backtest snapshot -> MACD: {}, ADX: {}, RSI: {}",
                macd.getMacdLine(), adx.getAdx(), rsi);

        // Add any demo/backtest logic you want here
    }
}
