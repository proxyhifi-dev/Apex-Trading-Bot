package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.dto.CandleConfirmationResult;
import com.apex.backend.dto.MacdConfirmationDto;
import com.apex.backend.model.Candle;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.CandleConfirmationValidator;
import com.apex.backend.service.indicator.MacdConfirmationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestEngineExitTest {

    @Test
    void exitsOnTimeStop() throws Exception {
        StrategyProperties strategyProperties = new StrategyProperties();
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        advanced.getBacktest().setTimeStopBars(3);
        advanced.getBacktest().setTimeStopMinMoveR(0.5);
        AtrService atrService = new AtrService(strategyProperties);
        MacdConfirmationService macdConfirmationService = mock(MacdConfirmationService.class);
        CandleConfirmationValidator candleConfirmationValidator = mock(CandleConfirmationValidator.class);
        BacktestResultRepository repo = mock(BacktestResultRepository.class);
        ExecutionCostModel executionCostModel = mock(ExecutionCostModel.class);
        when(executionCostModel.estimateExecution(any())).thenAnswer(invocation -> {
            ExecutionCostModel.ExecutionRequest req = invocation.getArgument(0);
            return new ExecutionCostModel.ExecutionEstimate(0, 0, 0, 0, 0, req.price(), 1);
        });
        when(macdConfirmationService.confirm(any())).thenReturn(new MacdConfirmationDto(0, 0, 0, true, false, false, false, false, false, false, false));
        when(candleConfirmationValidator.confirm(any())).thenReturn(new CandleConfirmationResult(true, false, true));

        BacktestEngine engine = new BacktestEngine(atrService, strategyProperties, advanced, macdConfirmationService, candleConfirmationValidator, repo, executionCostModel);
        List<Candle> candles = buildFlatCandles(50, 100);

        List<?> trades = simulate(engine, candles);
        assertThat(trades).isNotEmpty();
        Object trade = trades.get(0);
        int entryIndex = (int) getField(trade, "entryIndex");
        LocalDateTime exitTime = (LocalDateTime) getField(trade, "exitTime");
        int exitIndex = indexByTime(candles, exitTime);

        assertThat(exitIndex - entryIndex).isGreaterThanOrEqualTo(3);
    }

    @Test
    void trailsStopWithChandelier() throws Exception {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.getAtr().setTargetMultiplier(10.0);
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        advanced.getBacktest().setTimeStopBars(50);
        advanced.getBacktest().setChandelierAtrMult(1.0);
        AtrService atrService = new AtrService(strategyProperties);
        MacdConfirmationService macdConfirmationService = mock(MacdConfirmationService.class);
        CandleConfirmationValidator candleConfirmationValidator = mock(CandleConfirmationValidator.class);
        BacktestResultRepository repo = mock(BacktestResultRepository.class);
        ExecutionCostModel executionCostModel = mock(ExecutionCostModel.class);
        when(executionCostModel.estimateExecution(any())).thenAnswer(invocation -> {
            ExecutionCostModel.ExecutionRequest req = invocation.getArgument(0);
            return new ExecutionCostModel.ExecutionEstimate(0, 0, 0, 0, 0, req.price(), 1);
        });
        when(macdConfirmationService.confirm(any())).thenReturn(new MacdConfirmationDto(0, 0, 0, true, false, false, false, false, false, false, false));
        when(candleConfirmationValidator.confirm(any())).thenReturn(new CandleConfirmationResult(true, false, true));

        BacktestEngine engine = new BacktestEngine(atrService, strategyProperties, advanced, macdConfirmationService, candleConfirmationValidator, repo, executionCostModel);
        List<Candle> candles = buildTrendingThenDrop(60, 100);

        List<?> trades = simulate(engine, candles);
        assertThat(trades).isNotEmpty();
        Object trade = trades.get(0);
        double originalStop = (double) getField(trade, "originalStopLoss");
        double exitPrice = (double) getField(trade, "exit");

        assertThat(exitPrice).isGreaterThan(originalStop);
    }

    private List<?> simulate(BacktestEngine engine, List<Candle> candles) throws Exception {
        Method method = BacktestEngine.class.getDeclaredMethod("simulateTrades", List.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(engine, candles);
    }

    private Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private int indexByTime(List<Candle> candles, LocalDateTime time) {
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).getTimestamp().equals(time)) {
                return i;
            }
        }
        return -1;
    }

    private List<Candle> buildFlatCandles(int count, double price) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        for (int i = 0; i < count; i++) {
            candles.add(new Candle(price, price + 1, price - 1, price, 1000L, time.plusMinutes(i * 5L)));
        }
        return candles;
    }

    private List<Candle> buildTrendingThenDrop(int count, double start) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        double price = start;
        for (int i = 0; i < count - 1; i++) {
            double close = price + 1;
            candles.add(new Candle(price, close + 1, price - 0.5, close, 1200L, time.plusMinutes(i * 5L)));
            price = close;
        }
        candles.add(new Candle(price, price + 2, price - 5, price - 3, 1200L, time.plusMinutes((count - 1) * 5L)));
        return candles;
    }
}
