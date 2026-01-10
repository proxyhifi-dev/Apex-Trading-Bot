package com.apex.backend.trading.pipeline;

import com.apex.backend.config.ExecutionProperties;
import com.apex.backend.service.ExecutionCostModel;
import com.apex.backend.service.ExecutionCostModel.ExecutionRequest;
import com.apex.backend.service.ExecutionCostModel.ExecutionSide;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DefaultExecutionEngine implements ExecutionEngine {

    private final ExecutionCostModel executionCostModel;
    private final ExecutionProperties executionProperties;

    @Override
    public ExecutionPlan build(PipelineRequest request, SignalScore signalScore, RiskDecision riskDecision) {
        ExecutionPlan.ExecutionOrderType orderType = ExecutionPlan.ExecutionOrderType.valueOf(executionProperties.getDefaultOrderType());
        ExecutionRequest execRequest = new ExecutionRequest(
                request.symbol(),
                riskDecision.recommendedQuantity(),
                signalScore.entryPrice(),
                signalScore.suggestedStopLoss(),
                orderType == ExecutionPlan.ExecutionOrderType.MARKET ? ExecutionCostModel.OrderType.MARKET : ExecutionCostModel.OrderType.LIMIT,
                ExecutionSide.BUY,
                request.candles(),
                null,
                null,
                null
        );
        ExecutionCostModel.ExecutionEstimate estimate = executionCostModel.estimateExecution(execRequest);
        return new ExecutionPlan(
                orderType,
                MoneyUtils.bd(estimate.effectivePrice()),
                MoneyUtils.bd(estimate.totalCost()),
                estimate.fillProbability(),
                MoneyUtils.bd(estimate.spreadCost()),
                MoneyUtils.bd(estimate.slippageCost()),
                MoneyUtils.bd(estimate.marketImpactCost()),
                MoneyUtils.bd(estimate.latencyCost())
        );
    }
}
