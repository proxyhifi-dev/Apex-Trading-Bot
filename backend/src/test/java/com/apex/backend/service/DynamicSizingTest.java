package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.repository.TradeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DynamicSizingTest {

    @Test
    void resolvesMultiplierFloorCeilAndInterpolation() {
        StrategyProperties props = new StrategyProperties();
        props.getSizing().getDynamic().setEnabled(true);
        props.getSizing().getDynamic().setMinMultiplier(0.5);
        props.getSizing().getDynamic().setMaxMultiplier(1.0);
        props.getSizing().getDynamic().setScoreFloor(60);
        props.getSizing().getDynamic().setScoreCeil(90);

        TradeRepository tradeRepository = mock(TradeRepository.class);
        org.mockito.Mockito.when(tradeRepository.findTop50ByUserIdAndStatusOrderByExitTimeDesc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        HybridPositionSizingService service = new HybridPositionSizingService(new StrategyConfig(), props, new AdvancedTradingProperties(), tradeRepository);

        assertThat(service.resolveDynamicMultiplier(55.0)).isEqualTo(0.5);
        assertThat(service.resolveDynamicMultiplier(95.0)).isEqualTo(1.0);
        assertThat(service.resolveDynamicMultiplier(75.0)).isEqualTo(0.75);
    }

    @Test
    void appliesMultiplierBeforeCaps() {
        StrategyProperties props = new StrategyProperties();
        props.getSizing().getDynamic().setEnabled(true);
        props.getSizing().getDynamic().setMinMultiplier(0.5);
        props.getSizing().getDynamic().setMaxMultiplier(1.0);
        props.getSizing().getDynamic().setScoreFloor(60);
        props.getSizing().getDynamic().setScoreCeil(90);
        StrategyConfig config = new StrategyConfig();
        config.getRisk().setMaxSingleTradeCapitalPct(1.0);

        TradeRepository tradeRepository = mock(TradeRepository.class);
        org.mockito.Mockito.when(tradeRepository.findTop50ByUserIdAndStatusOrderByExitTimeDesc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        HybridPositionSizingService service = new HybridPositionSizingService(config, props, new AdvancedTradingProperties(), tradeRepository);
        HybridPositionSizingService.SizingResult result = service.calculateSizing(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(95),
                BigDecimal.ZERO,
                1L,
                75.0
        );

        assertThat(result.dynamicMultiplier()).isEqualTo(0.75);
        assertThat(result.quantity()).isEqualTo(150);
    }
}
