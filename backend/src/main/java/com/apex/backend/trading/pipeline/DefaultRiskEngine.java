package com.apex.backend.trading.pipeline;

import com.apex.backend.service.HybridPositionSizingService;
import com.apex.backend.service.LiquidityValidator;
import com.apex.backend.service.PortfolioHeatService;
import com.apex.backend.service.RiskManagementEngine;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultRiskEngine implements RiskEngine {

    private final RiskManagementEngine riskManagementEngine;
    private final PortfolioHeatService portfolioHeatService;
    private final LiquidityValidator liquidityValidator;
    private final HybridPositionSizingService hybridPositionSizingService;
    private final AtrService atrService;
    private final CorrelationRegimeService correlationRegimeService;

    @Override
    public RiskDecision evaluate(PipelineRequest request, SignalScore signalScore, PortfolioSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        if (!signalScore.tradable()) {
            reasons.add("Signal not tradable");
            return new RiskDecision(false, 0.0, reasons, 1.0, 0);
        }
        if (snapshot == null || snapshot.equity() == null || snapshot.equity().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("Invalid equity snapshot");
            return new RiskDecision(false, 0.0, reasons, 1.0, 0);
        }

        BigDecimal entry = MoneyUtils.bd(signalScore.entryPrice());
        BigDecimal stop = MoneyUtils.bd(signalScore.suggestedStopLoss());
        BigDecimal atr = MoneyUtils.ZERO;
        if (request.candles() != null && !request.candles().isEmpty()) {
            atr = MoneyUtils.bd(atrService.calculate(request.candles()).atr());
        }
        int qty = hybridPositionSizingService.calculateSizing(snapshot.equity(), entry, stop, atr, request.userId(), signalScore.score()).quantity();
        if (qty == 0) {
            reasons.add("Position size is zero");
            return new RiskDecision(false, 0.0, reasons, 1.0, 0);
        }

        var liquidityDecision = liquidityValidator.validate(request.symbol(), request.candles(), qty);
        if (!liquidityDecision.allowed()) {
            reasons.add("Liquidity validation failed");
            return new RiskDecision(false, 0.0, reasons, 1.0, 0);
        }
        qty = liquidityDecision.adjustedQty();

        boolean heatOk = portfolioHeatService.withinHeatLimit(request.userId(), snapshot.equity(), entry, stop, qty);
        boolean corrOk = portfolioHeatService.passesCorrelationCheck(request.symbol(), request.userId());
        boolean riskOk = riskManagementEngine.canExecuteTrade(snapshot.equity().doubleValue(), request.symbol(), entry.doubleValue(), stop.doubleValue(), qty);
        if (!heatOk) {
            reasons.add("Portfolio heat limit breached");
        }
        if (!corrOk) {
            reasons.add("Correlation guard rejected trade");
        }
        if (!riskOk) {
            reasons.add("Risk management rejected trade");
        }

        correlationRegimeService.updateRegime(request.userId());
        double sizingMultiplier = correlationRegimeService.getSizingMultiplier(request.userId());
        boolean allowed = heatOk && corrOk && riskOk;
        return new RiskDecision(allowed, allowed ? 1.0 : 0.0, reasons, sizingMultiplier, qty);
    }
}
