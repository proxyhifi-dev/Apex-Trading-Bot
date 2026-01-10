package com.apex.backend.service;

import com.apex.backend.config.AnalyticsProperties;
import com.apex.backend.config.RiskProperties;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.trading.pipeline.CorrelationRegimeService;
import com.apex.backend.trading.pipeline.PortfolioSnapshot;
import com.apex.backend.trading.pipeline.PortfolioEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioRiskAnalyticsService {

    private final TradeRepository tradeRepository;
    private final SettingsService settingsService;
    private final RiskOfRuinService riskOfRuinService;
    private final CvarService cvarService;
    private final AnalyticsProperties analyticsProperties;
    private final RiskProperties riskProperties;
    private final CorrelationRegimeService correlationRegimeService;
    private final PortfolioEngine portfolioEngine;

    public PortfolioRiskSnapshot getRiskSnapshot(Long userId) {
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        List<Trade> trades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(userId, isPaper, Trade.TradeStatus.CLOSED);
        List<Double> returns = trades.stream()
                .filter(trade -> trade.getRealizedPnl() != null && trade.getEntryPrice() != null)
                .map(trade -> trade.getRealizedPnl().doubleValue()
                        / (trade.getEntryPrice().doubleValue() * trade.getQuantity()))
                .toList();

        double winRate = trades.isEmpty() ? 0.0 : trades.stream()
                .filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl().doubleValue() > 0)
                .count() / (double) trades.size();
        double payoffRatio = calculatePayoffRatio(trades);
        double ruin = riskOfRuinService.calculate(
                winRate,
                payoffRatio,
                riskProperties.getRuin().getRiskPerTradePct(),
                riskProperties.getRuin().getBankrollR()
        );
        double cvar = cvarService.calculate(returns, analyticsProperties.getCvar().getConfidence() / 100.0);
        PortfolioSnapshot snapshot = portfolioEngine.snapshot(new com.apex.backend.trading.pipeline.PipelineRequest(
                userId,
                "",
                "",
                List.of(),
                null
        ));
        correlationRegimeService.updateRegime(userId);
        return new PortfolioRiskSnapshot(
                cvar,
                ruin,
                snapshot.heat(),
                correlationRegimeService.getSizingMultiplier(userId)
        );
    }

    private double calculatePayoffRatio(List<Trade> trades) {
        double totalWins = trades.stream()
                .filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl().doubleValue() > 0)
                .mapToDouble(trade -> trade.getRealizedPnl().doubleValue())
                .sum();
        double totalLoss = trades.stream()
                .filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl().doubleValue() < 0)
                .mapToDouble(trade -> Math.abs(trade.getRealizedPnl().doubleValue()))
                .sum();
        return totalLoss == 0 ? 0.0 : totalWins / totalLoss;
    }

    public record PortfolioRiskSnapshot(
            double cvar,
            double riskOfRuin,
            double portfolioHeat,
            double correlationSizingMultiplier
    ) {}
}
