package com.apex.backend.trading.pipeline;

import com.apex.backend.model.Candle;
import com.apex.backend.model.CorporateAction;
import com.apex.backend.service.CorporateActionService;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DefaultMarketDataProvider implements MarketDataProvider {

    private final FyersService fyersService;
    private final CorporateActionService corporateActionService;

    @Override
    public List<Candle> getCandles(String symbol, String timeframe, int bars) {
        List<Candle> candles = fyersService.getHistoricalData(symbol, bars, timeframe);
        List<CorporateAction> actions = getCorporateActions(symbol);
        return corporateActionService.applyAdjustments(candles, actions);
    }

    @Override
    public Optional<BidAsk> getBidAsk(String symbol) {
        try {
            var ltp = fyersService.getLtpBatch(List.of(symbol));
            if (ltp.containsKey(symbol)) {
                double mid = ltp.get(symbol).doubleValue();
                return Optional.of(new BidAsk(mid * 0.999, mid * 1.001));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    @Override
    public List<CorporateAction> getCorporateActions(String symbol) {
        return Collections.emptyList();
    }
}
