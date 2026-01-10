package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorSpotTest {

    @Test
    void atrMatchesWilderCalculation() {
        StrategyProperties properties = new StrategyProperties();
        properties.getAtr().setPeriod(3);
        AtrService atrService = new AtrService(properties);

        List<Candle> candles = buildTrendCandles();
        AtrService.AtrResult result = atrService.calculate(candles);

        assertThat(result.atr()).isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result.atrPercent()).isGreaterThan(0.0);
    }

    @Test
    void adxSpotCheckUptrend() {
        StrategyProperties properties = new StrategyProperties();
        properties.getAdx().setPeriod(3);
        AdxService adxService = new AdxService(properties);

        List<Candle> candles = buildExtendedTrendCandles();
        AdxService.AdxResult result = adxService.calculate(candles);

        assertThat(result.adx()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result.plusDI()).isGreaterThan(result.minusDI());
    }

    @Test
    void bollingerBandsSpotCheck() {
        StrategyProperties properties = new StrategyProperties();
        properties.getBollinger().setPeriod(3);
        properties.getBollinger().setDeviation(2.0);
        BollingerBandService service = new BollingerBandService(properties);

        List<Candle> candles = List.of(
                candle(10, 12, 9, 10),
                candle(11, 13, 10, 11),
                candle(12, 14, 11, 12)
        );
        BollingerBandService.BollingerBands bands = service.calculate(candles);

        assertThat(bands.middle()).isCloseTo(11.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(bands.upper()).isCloseTo(12.6329, org.assertj.core.data.Offset.offset(0.001));
        assertThat(bands.lower()).isCloseTo(9.3670, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void keltnerChannelSpotCheck() {
        StrategyProperties properties = new StrategyProperties();
        properties.getKeltner().setPeriod(3);
        properties.getKeltner().setAtrMultiplier(1.5);
        properties.getAtr().setPeriod(3);
        AtrService atrService = new AtrService(properties);
        KeltnerChannelService service = new KeltnerChannelService(properties, atrService);

        List<Candle> candles = List.of(
                candle(10, 12, 9, 10),
                candle(11, 13, 10, 11),
                candle(12, 14, 11, 12),
                candle(13, 15, 12, 13)
        );
        KeltnerChannelService.KeltnerChannel channel = service.calculate(candles);

        assertThat(channel.middle()).isCloseTo(12.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(channel.upper()).isCloseTo(16.5, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(channel.lower()).isCloseTo(7.5, org.assertj.core.data.Offset.offset(0.0001));
    }

    private List<Candle> buildTrendCandles() {
        return List.of(
                candle(10, 12, 9, 11),
                candle(11, 13, 10, 12),
                candle(12, 14, 11, 13),
                candle(13, 15, 12, 14)
        );
    }

    private List<Candle> buildExtendedTrendCandles() {
        return List.of(
                candle(10, 12, 9, 11),
                candle(11, 13, 10, 12),
                candle(12, 14, 11, 13),
                candle(13, 15, 12, 14),
                candle(14, 16, 13, 15),
                candle(15, 17, 14, 16)
        );
    }

    private Candle candle(double open, double high, double low, double close) {
        return new Candle(open, high, low, close, 1000L, LocalDateTime.now());
    }
}
