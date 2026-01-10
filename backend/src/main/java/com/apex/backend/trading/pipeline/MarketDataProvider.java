package com.apex.backend.trading.pipeline;

import com.apex.backend.model.Candle;
import com.apex.backend.model.CorporateAction;

import java.util.List;
import java.util.Optional;

public interface MarketDataProvider {
    List<Candle> getCandles(String symbol, String timeframe, int bars);

    Optional<BidAsk> getBidAsk(String symbol);

    List<CorporateAction> getCorporateActions(String symbol);

    record BidAsk(double bid, double ask) {}
}
