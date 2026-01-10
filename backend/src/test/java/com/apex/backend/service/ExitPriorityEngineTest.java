package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExitPriorityEngineTest {

    @Test
    void triggersHardStop() {
        StrategyProperties properties = new StrategyProperties();
        ExitPriorityEngine engine = new ExitPriorityEngine(properties);
        Trade trade = Trade.builder()
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(BigDecimal.valueOf(100))
                .stopLoss(BigDecimal.valueOf(95))
                .currentStopLoss(BigDecimal.valueOf(95))
                .atr(BigDecimal.valueOf(2))
                .build();

        ExitPriorityEngine.ExitDecision decision = engine.evaluate(trade, BigDecimal.valueOf(94), 1, false);
        assertThat(decision.shouldExit()).isTrue();
        assertThat(decision.reason()).isEqualTo(Trade.ExitReason.STOP_LOSS);
    }
}
