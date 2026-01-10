package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.ExecutionCost;
import com.apex.backend.repository.ExecutionCostRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExecutionCostModel {

    private final AdvancedTradingProperties advancedTradingProperties;
    private final ExecutionCostRepository executionCostRepository;

    public ExecutionCost estimateCost(String clientOrderId, String symbol, int quantity, double price, double atr) {
        AdvancedTradingProperties.ExecutionCost cfg = advancedTradingProperties.getExecutionCost();
        double notional = price * quantity;
        double spreadCost = notional * (cfg.getSpreadBps() / 10000.0);
        double slippageCost = atr * cfg.getSlippageAtrPct() * quantity;
        double commissionCost = notional * cfg.getCommissionPct();
        double taxBase = notional * (cfg.getSttPct() + cfg.getTxnPct() + cfg.getSebiPct());
        double taxCost = taxBase + (taxBase * cfg.getGstPct());
        double expected = spreadCost + slippageCost + commissionCost + taxCost;

        ExecutionCost cost = ExecutionCost.builder()
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .quantity(quantity)
                .expectedCost(MoneyUtils.bd(expected))
                .spreadCost(MoneyUtils.bd(spreadCost))
                .slippageCost(MoneyUtils.bd(slippageCost))
                .commissionCost(MoneyUtils.bd(commissionCost))
                .taxCost(MoneyUtils.bd(taxCost))
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
}
