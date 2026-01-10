package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.service.marketdata.FyersMarketDataClient;
import com.apex.backend.service.marketdata.FyersQuote;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RiskGatekeeper {

    private final StrategyConfig strategyConfig;
    private final StrategyProperties strategyProperties;
    private final PortfolioService portfolioService;
    private final PortfolioHeatService portfolioHeatService;
    private final RiskManagementEngine riskManagementEngine;
    private final FyersAuthService fyersAuthService;
    private final FyersMarketDataClient marketDataClient;
    private final AdvancedTradingProperties advancedTradingProperties;

    public RiskGateDecision evaluate(RiskGateRequest request) {
        if (request.exitOrder()) {
            return checkLiquidityAndSpread(request);
        }

        double equity = portfolioService.getAvailableEquity(request.paper(), request.userId());
        double dailyLossLimitPct = strategyProperties.getCircuit().getDailyLossLimit() * 100.0;
        if (riskManagementEngine.getDailyLossPercent(equity) > dailyLossLimitPct) {
            return RiskGateDecision.reject(RiskRejectCode.DAILY_LOSS_LIMIT, "Daily loss limit breached");
        }

        int openPositions = portfolioService.getOpenPositionCount(request.paper(), request.userId());
        if (openPositions >= strategyConfig.getRisk().getMaxOpenPositions()) {
            return RiskGateDecision.reject(RiskRejectCode.MAX_OPEN_POSITIONS, "Max open positions reached");
        }

        boolean correlationOk = portfolioHeatService.passesCorrelationCheck(request.symbol(), request.userId());
        if (!correlationOk) {
            return RiskGateDecision.reject(RiskRejectCode.CORRELATION_LIMIT, "Correlation limit breached");
        }

        BigDecimal entryPrice = MoneyUtils.bd(request.referencePrice());
        BigDecimal stopLoss = request.stopLoss() != null ? MoneyUtils.bd(request.stopLoss()) : entryPrice;
        boolean heatOk = portfolioHeatService.withinHeatLimit(
                request.userId(),
                MoneyUtils.bd(equity),
                entryPrice,
                stopLoss,
                request.quantity()
        );
        if (!heatOk) {
            return RiskGateDecision.reject(RiskRejectCode.PORTFOLIO_HEAT_LIMIT, "Portfolio heat limit breached");
        }

        return checkLiquidityAndSpread(request);
    }

    private RiskGateDecision checkLiquidityAndSpread(RiskGateRequest request) {
        String token = request.paper() ? null : fyersAuthService.getFyersToken(request.userId());
        Optional<FyersQuote> quote = marketDataClient.getQuote(request.symbol(), token);
        if (quote.isEmpty()) {
            return RiskGateDecision.reject(RiskRejectCode.LIQUIDITY_DATA_MISSING, "Missing bid/ask data");
        }
        FyersQuote marketQuote = quote.get();
        if (marketQuote.bidPrice() == null || marketQuote.askPrice() == null) {
            return RiskGateDecision.reject(RiskRejectCode.LIQUIDITY_DATA_MISSING, "Missing bid/ask data");
        }
        double bid = marketQuote.bidPrice().doubleValue();
        double ask = marketQuote.askPrice().doubleValue();
        if (bid <= 0 || ask <= 0) {
            return RiskGateDecision.reject(RiskRejectCode.LIQUIDITY_DATA_MISSING, "Invalid bid/ask data");
        }
        double mid = (bid + ask) / 2.0;
        double spreadPct = ((ask - bid) / mid) * 100.0;
        if (spreadPct > advancedTradingProperties.getLiquidity().getMaxSpreadPct()) {
            return RiskGateDecision.reject(RiskRejectCode.SPREAD_TOO_WIDE, "Spread too wide: " + spreadPct + "%");
        }
        return RiskGateDecision.allow();
    }

    public record RiskGateRequest(
            Long userId,
            String symbol,
            int quantity,
            boolean paper,
            double referencePrice,
            Double stopLoss,
            boolean exitOrder
    ) {}

    public record RiskGateDecision(boolean allowed, RiskRejectCode reason, String message) {
        static RiskGateDecision allow() {
            return new RiskGateDecision(true, null, null);
        }

        static RiskGateDecision reject(RiskRejectCode reason, String message) {
            return new RiskGateDecision(false, reason, message);
        }
    }

    public enum RiskRejectCode {
        PORTFOLIO_HEAT_LIMIT,
        DAILY_LOSS_LIMIT,
        CORRELATION_LIMIT,
        MAX_OPEN_POSITIONS,
        SPREAD_TOO_WIDE,
        LIQUIDITY_DATA_MISSING
    }
}
