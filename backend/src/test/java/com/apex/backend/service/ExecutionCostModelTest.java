package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.ExecutionProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.repository.ExecutionCostRepository;
import com.apex.backend.service.ExecutionCostModel.ExecutionEstimate;
import com.apex.backend.service.ExecutionCostModel.ExecutionRequest;
import com.apex.backend.service.ExecutionCostModel.ExecutionSide;
import com.apex.backend.service.ExecutionCostModel.OrderType;
import com.apex.backend.service.indicator.AtrService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutionCostModelTest {

    @Test
    void estimateExecutionAccountsForSpreadSlippageImpactAndLatency() {
        ExecutionProperties executionProperties = new ExecutionProperties();
        executionProperties.setSpreadPct(0.001);
        executionProperties.setSlippageAtrPct(0.1);
        executionProperties.setImpactFactor(0.05);
        executionProperties.setAvgDailyNotional(1_000_000);
        executionProperties.setLatencyMillis(1000);
        executionProperties.setLatencyMovePctPerSecond(0.0005);
        executionProperties.setLimitFillMaxDistancePct(0.01);
        executionProperties.setLimitFillVolumeFactor(0.2);

        ExecutionCostModel model = new ExecutionCostModel(
                new AdvancedTradingProperties(),
                executionProperties,
                mock(ExecutionCostRepository.class),
                new AtrService(new StrategyProperties())
        );

        ExecutionRequest request = new ExecutionRequest(
                "TEST",
                10,
                100.0,
                100.0,
                OrderType.MARKET,
                ExecutionSide.BUY,
                java.util.List.of(),
                2.0
        );
        ExecutionEstimate estimate = model.estimateExecution(request);

        double spreadCost = 100.0 * 0.001;
        double slippageCost = 2.0 * 0.1;
        double marketImpact = Math.sqrt((100.0 * 10) / 1_000_000.0) * 0.05 * 100.0;
        double latencyCost = 100.0 * 0.0005;
        double expectedPerShare = spreadCost + slippageCost + marketImpact + latencyCost;

        assertThat(estimate.spreadCost()).isCloseTo(spreadCost, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(estimate.slippageCost()).isCloseTo(slippageCost, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(estimate.marketImpactCost()).isCloseTo(marketImpact, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(estimate.latencyCost()).isCloseTo(latencyCost, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(estimate.totalCost()).isCloseTo(expectedPerShare * 10, org.assertj.core.data.Offset.offset(0.001));
        assertThat(estimate.effectivePrice()).isCloseTo(100.0 + expectedPerShare, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void limitOrderFillProbabilityDropsWithDistance() {
        ExecutionProperties executionProperties = new ExecutionProperties();
        executionProperties.setSpreadPct(0.001);
        executionProperties.setSlippageAtrPct(0.1);
        executionProperties.setImpactFactor(0.05);
        executionProperties.setAvgDailyNotional(1_000_000);
        executionProperties.setLatencyMillis(1000);
        executionProperties.setLatencyMovePctPerSecond(0.0005);
        executionProperties.setLimitFillMaxDistancePct(0.01);
        executionProperties.setLimitFillVolumeFactor(0.2);

        ExecutionCostModel model = new ExecutionCostModel(
                new AdvancedTradingProperties(),
                executionProperties,
                mock(ExecutionCostRepository.class),
                new AtrService(new StrategyProperties())
        );

        ExecutionRequest request = new ExecutionRequest(
                "TEST",
                10,
                100.0,
                101.0,
                OrderType.LIMIT,
                ExecutionSide.BUY,
                java.util.List.of(),
                2.0
        );
        ExecutionEstimate estimate = model.estimateExecution(request);

        assertThat(estimate.fillProbability()).isBetween(0.0, 1.0);
        assertThat(estimate.fillProbability()).isLessThan(1.0);
    }
}
