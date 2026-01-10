package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.model.MarketRegime;
import com.apex.backend.model.MarketRegimeHistory;
import com.apex.backend.repository.MarketRegimeHistoryRepository;
import com.apex.backend.service.DecisionAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketRegimeDetector {

    private final AdxService adxService;
    private final AtrService atrService;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final MarketRegimeHistoryRepository marketRegimeHistoryRepository;
    private final DecisionAuditService decisionAuditService;

    public MarketRegime detectAndStore(String symbol, String timeframe, List<Candle> candles) {
        AdxService.AdxResult adx = adxService.calculate(candles);
        AtrService.AtrResult atr = atrService.calculate(candles);
        AdvancedTradingProperties.MarketRegime config = advancedTradingProperties.getMarketRegime();

        MarketRegime regime;
        if (atr.atrPercent() >= config.getHighVolAtrPercent()) {
            regime = MarketRegime.HIGH_VOL;
        } else if (atr.atrPercent() <= config.getLowVolAtrPercent()) {
            regime = MarketRegime.LOW_VOL;
        } else if (adx.adx() >= config.getTrendingAdxThreshold()) {
            regime = MarketRegime.TRENDING;
        } else {
            regime = MarketRegime.RANGE;
        }

        MarketRegimeHistory history = MarketRegimeHistory.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .regime(regime)
                .adx(adx.adx())
                .atrPercent(atr.atrPercent())
                .detectedAt(LocalDateTime.now())
                .build();
        marketRegimeHistoryRepository.save(history);

        decisionAuditService.record(symbol, timeframe, "REGIME", Map.of(
                "regime", regime.name(),
                "adx", adx.adx(),
                "atrPercent", atr.atrPercent()
        ));

        return regime;
    }
}
