package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExitManagerTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private PaperTradingService paperTradingService;

    @Mock
    private FyersService fyersService;

    @Mock
    private RiskManagementEngine riskManagementEngine;

    private StrategyConfig strategyConfig;

    private ExitManager exitManager;

    @BeforeEach
    void setUp() {
        strategyConfig = new StrategyConfig();
        StrategyConfig.Risk risk = new StrategyConfig.Risk();
        risk.setTargetMultiplier(2.0);
        strategyConfig.setRisk(risk);
        exitManager = new ExitManager(tradeRepository, paperTradingService, fyersService, strategyConfig, riskManagementEngine);
    }

    @Test
    void manageExits_closesTradeOnStopLoss() {
        Trade trade = Trade.builder()
                .id(1L)
                .symbol("NSE:ABC-EQ")
                .tradeType(Trade.TradeType.LONG)
                .quantity(10)
                .entryPrice(100.0)
                .stopLoss(95.0)
                .currentStopLoss(95.0)
                .atr(2.0)
                .highestPrice(100.0)
                .isPaperTrade(true)
                .status(Trade.TradeStatus.OPEN)
                .build();

        when(tradeRepository.findByStatus(Trade.TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(fyersService.getLtpBatch(List.of("NSE:ABC-EQ"))).thenReturn(Map.of("NSE:ABC-EQ", 94.0));
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);

        exitManager.manageExits();

        assertThat(trade.getStatus()).isEqualTo(Trade.TradeStatus.CLOSED);
        assertThat(trade.getExitReason()).isEqualTo(Trade.ExitReason.STOP_LOSS);
        assertThat(trade.getExitPrice()).isEqualTo(94.0);
        assertThat(trade.getExitTime()).isNotNull();
        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
        verify(riskManagementEngine).removeOpenPosition(trade.getSymbol());
    }

    @Test
    void manageExits_closesTradeOnTarget() {
        Trade trade = Trade.builder()
                .id(2L)
                .symbol("NSE:XYZ-EQ")
                .tradeType(Trade.TradeType.LONG)
                .quantity(5)
                .entryPrice(100.0)
                .stopLoss(95.0)
                .currentStopLoss(95.0)
                .atr(2.0)
                .highestPrice(100.0)
                .isPaperTrade(true)
                .status(Trade.TradeStatus.OPEN)
                .build();

        when(tradeRepository.findByStatus(Trade.TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(fyersService.getLtpBatch(List.of("NSE:XYZ-EQ"))).thenReturn(Map.of("NSE:XYZ-EQ", 105.0));
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);

        exitManager.manageExits();

        assertThat(trade.getStatus()).isEqualTo(Trade.TradeStatus.CLOSED);
        assertThat(trade.getExitReason()).isEqualTo(Trade.ExitReason.TARGET);
        assertThat(trade.getExitPrice()).isEqualTo(105.0);
        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
        verify(riskManagementEngine).removeOpenPosition(trade.getSymbol());
    }
}
