package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.ExecutionProperties;
import com.apex.backend.model.ExecutionCost;
import com.apex.backend.repository.ExecutionCostRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecutionCostModel {

    private final AdvancedTradingProperties advancedTradingProperties;
    private final ExecutionProperties executionProperties;
    private final ExecutionCostRepository executionCostRepository;
    private final com.apex.backend.service.indicator.AtrService atrService;

    public ExecutionCost estimateCost(String clientOrderId, ExecutionRequest request) {
        ExecutionEstimate estimate = estimateExecution(request);
        CostBreakdown breakdown = computeCostBreakdown(request, estimate);
        double expected = breakdown.totalCost();

        ExecutionCost cost = ExecutionCost.builder()
                .clientOrderId(clientOrderId)
                .symbol(request.symbol)
                .quantity(request.quantity)
                .expectedCost(MoneyUtils.bd(expected))
                .spreadCost(MoneyUtils.bd(estimate.spreadCost))
                .slippageCost(MoneyUtils.bd(estimate.slippageCost))
                .commissionCost(MoneyUtils.bd(breakdown.commissionCost()))
                .taxCost(MoneyUtils.bd(breakdown.taxCost()))
                .createdAt(LocalDateTime.now())
                .build();
        return executionCostRepository.save(cost);
    }

    public void updateRealizedCost(String clientOrderId, double realizedCost, String orderId) {
        executionCostRepository.findByClientOrderId(clientOrderId).ifPresent(cost -> {
            cost.setRealizedCost(MoneyUtils.bd(realizedCost));
            cost.setOrderId(orderId);
            cost.setUpdatedAt(LocalDateTime.now());
            executionCostRepository.save(cost);
        });
    }

    public ExecutionEstimate estimateExecution(ExecutionRequest request) {
        double atr = request.atrOverride != null
                ? request.atrOverride
                : request.candles != null && !request.candles.isEmpty()
                ? atrService.calculate(request.candles).atr()
                : Math.abs(request.price - request.limitPrice) * 0.5;
        boolean hasBidAsk = request.bidPrice != null && request.askPrice != null
                && request.bidPrice > 0 && request.askPrice > 0;
        double spreadCost = hasBidAsk
                ? Math.max(0.0, (request.askPrice - request.bidPrice) / 2.0)
                : request.price * executionProperties.getSpreadPct();
        double slippageCost = hasBidAsk ? 0.0 : atr * executionProperties.getSlippageAtrPct();
        double notional = request.price * request.quantity;
        double marketImpactCost = Math.sqrt(notional / executionProperties.getAvgDailyNotional())
                * executionProperties.getImpactFactor() * request.price;
        double latencyCost = request.price * executionProperties.getLatencyMovePctPerSecond()
                * (executionProperties.getLatencyMillis() / 1000.0);
        double totalPerShare = spreadCost + slippageCost + marketImpactCost + latencyCost;
        double basePrice = request.price;
        if (hasBidAsk) {
            basePrice = request.side == ExecutionSide.BUY ? request.askPrice : request.bidPrice;
        }
        double fillProbability = 1.0;
        if (request.orderType == OrderType.LIMIT) {
            double distancePct = Math.abs(request.limitPrice - request.price) / request.price;
            double distanceFactor = Math.max(0.0, 1.0 - (distancePct / executionProperties.getLimitFillMaxDistancePct()));
            double volumeFactor = Math.min(1.0, (notional / executionProperties.getAvgDailyNotional())
                    / executionProperties.getLimitFillVolumeFactor());
            fillProbability = distanceFactor * volumeFactor;
        }
        double effectivePrice = request.side == ExecutionSide.BUY
                ? basePrice + totalPerShare
                : basePrice - totalPerShare;
        return new ExecutionEstimate(
                spreadCost,
                slippageCost,
                marketImpactCost,
                latencyCost,
                totalPerShare * request.quantity,
                effectivePrice,
                fillProbability
        );
    }

    public double calculateAllInCost(ExecutionRequest request) {
        ExecutionEstimate estimate = estimateExecution(request);
        CostBreakdown breakdown = computeCostBreakdown(request, estimate);
        return breakdown.totalCost();
    }

    private CostBreakdown computeCostBreakdown(ExecutionRequest request, ExecutionEstimate estimate) {
        AdvancedTradingProperties.ExecutionCost cfg = advancedTradingProperties.getExecutionCost();
        double notional = request.price * request.quantity;
        double commissionCost = notional * cfg.getCommissionPct();
        double taxBase = notional * (cfg.getSttPct() + cfg.getTxnPct() + cfg.getSebiPct());
        double taxCost = taxBase + (taxBase * cfg.getGstPct());
        double total = estimate.totalCost + commissionCost + taxCost;
        return new CostBreakdown(commissionCost, taxCost, total);
    }

    private record CostBreakdown(double commissionCost, double taxCost, double totalCost) {}

    public record ExecutionRequest(
            String symbol,
            int quantity,
            double price,
            double limitPrice,
            OrderType orderType,
            ExecutionSide side,
            List<com.apex.backend.model.Candle> candles,
            Double atrOverride,
            Double bidPrice,
            Double askPrice
    ) {}

    public record ExecutionEstimate(
            double spreadCost,
            double slippageCost,
            double marketImpactCost,
            double latencyCost,
            double totalCost,
            double effectivePrice,
            double fillProbability
    ) {}

    public enum OrderType {
        MARKET,
        LIMIT
    }

    public enum ExecutionSide {
        BUY,
        SELL
    }
}
