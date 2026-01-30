package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExitManagerTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private FyersService fyersService;

    @Mock
    private ExitPriorityEngine exitPriorityEngine;

    @Mock
    private ExecutionEngine executionEngine;

    @Mock
    private TradeCloseService tradeCloseService;

    @Mock
    private ExitRetryService exitRetryService;

    private StrategyProperties strategyProperties;

    private ExitManager exitManager;

    @BeforeEach
    void setUp() {
        strategyProperties = new StrategyProperties();
        exitManager = new ExitManager(tradeRepository, fyersService, strategyProperties, exitPriorityEngine, executionEngine,
                tradeCloseService, exitRetryService);
    }

    @Test
    void manageExits_closesTradeOnExitDecision() {
        Trade trade = Trade.builder()
                .id(1L)
                .symbol("NSE:ABC-EQ")
                .tradeType(Trade.TradeType.LONG)
                .quantity(10)
                .entryPrice(BigDecimal.valueOf(100))
                .stopLoss(BigDecimal.valueOf(95))
                .currentStopLoss(BigDecimal.valueOf(95))
                .atr(BigDecimal.valueOf(2))
                .highestPrice(BigDecimal.valueOf(100))
                .isPaperTrade(true)
                .entryTime(LocalDateTime.now().minusMinutes(10))
                .status(Trade.TradeStatus.OPEN)
                .build();

        when(tradeRepository.findByUserIdAndStatus(1L, Trade.TradeStatus.OPEN)).thenReturn(List.of(trade));
        when(fyersService.getLtpBatch(List.of("NSE:ABC-EQ"))).thenReturn(Map.of("NSE:ABC-EQ", BigDecimal.valueOf(94)));
        when(exitPriorityEngine.evaluate(eq(trade), any(BigDecimal.class), any(Integer.class), eq(false)))
                .thenReturn(ExitPriorityEngine.ExitDecision.exit(BigDecimal.valueOf(94), BigDecimal.valueOf(95),
                        Trade.ExitReason.STOP_LOSS, "STOP"));
        when(executionEngine.execute(any())).thenReturn(new ExecutionEngine.ExecutionResult(
                "CLIENT", "BRK", ExecutionEngine.ExecutionStatus.FILLED, 10, BigDecimal.valueOf(94), null
        ));

        exitManager.manageExits(1L);

        verify(tradeCloseService).finalizeTrade(eq(trade), eq(BigDecimal.valueOf(94)), eq(Trade.ExitReason.STOP_LOSS), eq("STOP"));
    }
}
