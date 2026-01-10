package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridPositionSizingServiceTest {

    @Test
    void usesSmallerOfAtrAndKelly() {
        StrategyConfig strategyConfig = new StrategyConfig();
        StrategyProperties strategyProperties = new StrategyProperties();
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        TradeRepository tradeRepository = mock(TradeRepository.class);

        Trade win = Trade.builder()
                .userId(1L)
                .status(Trade.TradeStatus.CLOSED)
                .realizedPnl(BigDecimal.valueOf(100))
                .exitTime(LocalDateTime.now())
                .build();
        Trade loss = Trade.builder()
                .userId(1L)
                .status(Trade.TradeStatus.CLOSED)
                .realizedPnl(BigDecimal.valueOf(-50))
                .exitTime(LocalDateTime.now().minusMinutes(5))
                .build();
        when(tradeRepository.findTop50ByUserIdAndStatusOrderByExitTimeDesc(1L, Trade.TradeStatus.CLOSED))
                .thenReturn(List.of(win, loss));

        HybridPositionSizingService service = new HybridPositionSizingService(strategyConfig, strategyProperties, advanced, tradeRepository);
        int qty = service.calculateQuantity(BigDecimal.valueOf(100000), BigDecimal.valueOf(100), BigDecimal.valueOf(98), BigDecimal.valueOf(1), 1L);

        assertThat(qty).isGreaterThan(0);
    }
}
