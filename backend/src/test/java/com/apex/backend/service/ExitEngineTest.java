package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitEngineTest {

    @Test
    void movesBreakevenAtConfiguredR() {
        StrategyConfig config = new StrategyConfig();
        config.getRisk().setBreakevenMoveR(1.0);
        config.getRisk().setBreakevenOffsetR(0.1);
        ExitEngine engine = new ExitEngine(config);
        ExitEngine.TradeState state = new ExitEngine.TradeState(100.0, 95.0, 10);

        ExitEngine.ExitDecision decision = engine.manageTrade(state, 105.0, 2.0, false);

        assertTrue(state.isBreakevenMoved());
        assertEquals(100.5, state.getCurrentStopLoss(), 1e-6);
        assertTrue(!decision.isShouldExit());
    }

    @Test
    void appliesTrailingAfterConfiguredR() {
        StrategyConfig config = new StrategyConfig();
        config.getRisk().setTrailingStartR(2.0);
        config.getRisk().setTrailingAtrMultiplier(1.5);
        ExitEngine engine = new ExitEngine(config);
        ExitEngine.TradeState state = new ExitEngine.TradeState(100.0, 95.0, 10);

        engine.manageTrade(state, 110.0, 2.0, false);

        assertEquals(107.0, state.getCurrentStopLoss(), 1e-6);
    }

    @Test
    void exitsAtStopOrTarget() {
        StrategyConfig config = new StrategyConfig();
        config.getRisk().setTargetMultiplier(3.0);
        ExitEngine engine = new ExitEngine(config);
        ExitEngine.TradeState state = new ExitEngine.TradeState(100.0, 95.0, 10);

        ExitEngine.ExitDecision stopped = engine.manageTrade(state, 94.0, 2.0, false);
        assertTrue(stopped.isShouldExit());
        assertEquals("STOP/TRAIL", stopped.getReason());

        ExitEngine.TradeState targetState = new ExitEngine.TradeState(100.0, 95.0, 10);
        ExitEngine.ExitDecision target = engine.manageTrade(targetState, 115.0, 2.0, false);
        assertTrue(target.isShouldExit());
        assertEquals("TARGET", target.getReason());
    }
}
