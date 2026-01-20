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
    private final TradeCooldownService tradeCooldownService;
    private final CrisisModeService crisisModeService;

    public RiskGateDecision evaluate(RiskGateRequest request) {
        if (crisisModeService.isCrisisModeActive()) {
            return RiskGateDecision.reject(
                RiskRejectCode.CRISIS_MODE,
                "Crisis mode active",
                null,
                null,
                request.symbol(),
                null
            );
        }
        if (request.exitOrder()) {
            return checkLiquidityAndSpread(request);
        }

        double equity = portfolioService.getAvailableEquity(request.paper(), request.userId());
        double dailyLossLimitPct = strategyProperties.getCircuit().getDailyLossLimit() * 100.0;
        double currentDailyLossPct = riskManagementEngine.getDailyLossPercent(equity);
        if (currentDailyLossPct > dailyLossLimitPct) {
            return RiskGateDecision.reject(
                RiskRejectCode.DAILY_LOSS_LIMIT,
                "Daily loss limit breached",
                dailyLossLimitPct,
                currentDailyLossPct,
                request.symbol(),
                null
            );
        }

        int openPositions = portfolioService.getOpenPositionCount(request.paper(), request.userId());
        int maxPositions = strategyConfig.getRisk().getMaxOpenPositions();
        if (openPositions >= maxPositions) {
            return RiskGateDecision.reject(
                RiskRejectCode.MAX_OPEN_POSITIONS,
                "Max open positions reached",
                (double) maxPositions,
                (double) openPositions,
                request.symbol(),
                null
            );
        }

        if (tradeCooldownService.isInCooldown(request.symbol(), request.userId())) {
            long remainingSeconds = tradeCooldownService.getRemainingCooldown(request.symbol(), request.userId());
            return RiskGateDecision.reject(
                RiskRejectCode.COOLDOWN,
                "Symbol in cooldown",
                null,
                (double) remainingSeconds,
                request.symbol(),
                null
            );
        }

        boolean correlationOk = portfolioHeatService.passesCorrelationCheck(request.symbol(), request.userId());
        if (!correlationOk) {
            return RiskGateDecision.reject(
                RiskRejectCode.CORRELATION_LIMIT,
                "Correlation limit breached",
                null,
                null,
                request.symbol(),
                null
            );
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
            double currentHeat = portfolioHeatService.currentPortfolioHeat(request.userId(), MoneyUtils.bd(equity));
            double maxHeat = advancedTradingProperties.getRisk().getMaxPortfolioHeatPct() * 100.0;
            return RiskGateDecision.reject(
                RiskRejectCode.PORTFOLIO_HEAT_LIMIT,
                "Portfolio heat limit breached",
                maxHeat,
                currentHeat,
                request.symbol(),
                null
            );
        }

        return checkLiquidityAndSpread(request);
    }

    private RiskGateDecision checkLiquidityAndSpread(RiskGateRequest request) {
        String token = request.paper() ? null : fyersAuthService.getFyersToken(request.userId());
        Optional<FyersQuote> quote = marketDataClient.getQuote(request.symbol(), token);
        if (quote.isEmpty()) {
            return RiskGateDecision.reject(
                RiskRejectCode.LIQUIDITY_DATA_MISSING,
                "Missing bid/ask data",
                null,
                null,
                request.symbol(),
                null
            );
        }
        FyersQuote marketQuote = quote.get();
        if (marketQuote.bidPrice() == null || marketQuote.askPrice() == null) {
            return RiskGateDecision.reject(
                RiskRejectCode.LIQUIDITY_DATA_MISSING,
                "Missing bid/ask data",
                null,
                null,
                request.symbol(),
                null
            );
        }
        double bid = marketQuote.bidPrice().doubleValue();
        double ask = marketQuote.askPrice().doubleValue();
        if (bid <= 0 || ask <= 0) {
            return RiskGateDecision.reject(
                RiskRejectCode.LIQUIDITY_DATA_MISSING,
                "Invalid bid/ask data",
                null,
                null,
                request.symbol(),
                null
            );
        }
        double mid = (bid + ask) / 2.0;
        double spreadPct = ((ask - bid) / mid) * 100.0;
        double maxSpreadPct = advancedTradingProperties.getLiquidity().getMaxSpreadPct();
        if (spreadPct > maxSpreadPct) {
            return RiskGateDecision.reject(
                RiskRejectCode.SPREAD_TOO_WIDE,
                "Spread too wide: " + spreadPct + "%",
                maxSpreadPct,
                spreadPct,
                request.symbol(),
                null
            );
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

    public record RiskGateDecision(
            boolean allowed,
            RiskRejectCode reason,
            String message,
            Double threshold,      // Threshold value that was exceeded
            Double currentValue,   // Current value that triggered rejection
            String symbol,         // Symbol associated with rejection
            Long signalId          // Signal ID if available
    ) {
        static RiskGateDecision allow() {
            return new RiskGateDecision(true, null, null, null, null, null, null);
        }

        static RiskGateDecision reject(RiskRejectCode reason, String message, Double threshold, Double currentValue, String symbol, Long signalId) {
            return new RiskGateDecision(false, reason, message, threshold, currentValue, symbol, signalId);
        }
        
        // Backward compatibility
        static RiskGateDecision reject(RiskRejectCode reason, String message) {
            return reject(reason, message, null, null, null, null);
        }
    }

    public enum RiskRejectCode {
        PORTFOLIO_HEAT_LIMIT,
        DAILY_LOSS_LIMIT,
        CORRELATION_LIMIT,
        MAX_OPEN_POSITIONS,
        MAX_POSITIONS,           // Alias for MAX_OPEN_POSITIONS
        SPREAD_TOO_WIDE,
        LIQUIDITY_DATA_MISSING,
        COOLDOWN,                // Symbol in cooldown period
        DRAWDOWN_LIMIT,          // Portfolio drawdown exceeded
        CONSEC_LOSSES,           // Consecutive losses limit
        CRISIS_MODE,             // Crisis mode active
        DATA_STALE,              // Market data is stale
        DATA_GAP,                // Missing candles/gaps in data
        CORP_ACTION_BLACKOUT,    // Corporate action blackout
        MARKET_CLOSED,           // Market is closed
        MANUAL_HALTED            // Trading manually halted
    }
}
