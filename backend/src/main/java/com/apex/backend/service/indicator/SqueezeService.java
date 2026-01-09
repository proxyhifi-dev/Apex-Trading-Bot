package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SqueezeService {

    private final StrategyProperties strategyProperties;
    private final BollingerBandService bollingerBandService;
    private final KeltnerChannelService keltnerChannelService;

    public SqueezeResult detect(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new SqueezeResult(false, 0, 0);
        }
        int minBars = strategyProperties.getSqueeze().getMinBars();
        double tightThreshold = strategyProperties.getSqueeze().getTightThreshold();
        int count = 0;
        double lastRatio = 0;

        for (int i = candles.size() - 1; i >= 0; i--) {
            BollingerBandService.BollingerBands bands = bollingerBandService.calculate(candles, i);
            KeltnerChannelService.KeltnerChannel channel = keltnerChannelService.calculate(candles, i);
            if (bands.width() == 0 || channel.width() == 0) {
                break;
            }
            boolean inside = bands.upper() < channel.upper() && bands.lower() > channel.lower();
            double ratio = bands.width() / channel.width();
            boolean tight = ratio < tightThreshold;
            if (inside && tight) {
                count++;
                lastRatio = ratio;
            } else {
                break;
            }
        }

        boolean isSqueeze = count >= minBars;
        return new SqueezeResult(isSqueeze, count, lastRatio);
    }

    public record SqueezeResult(boolean squeeze, int bars, double ratio) {}
}
