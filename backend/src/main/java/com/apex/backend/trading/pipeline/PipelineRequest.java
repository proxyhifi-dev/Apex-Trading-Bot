package com.apex.backend.trading.pipeline;

import com.apex.backend.model.Candle;

import java.util.List;

public record PipelineRequest(
        Long userId,
        String symbol,
        String timeframe,
        List<Candle> candles,
        PortfolioSnapshot portfolioSnapshot
) {}
