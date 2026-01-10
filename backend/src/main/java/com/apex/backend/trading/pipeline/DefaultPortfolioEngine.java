package com.apex.backend.trading.pipeline;

import com.apex.backend.service.PortfolioHeatService;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioEngine implements PortfolioEngine {

    private final PortfolioService portfolioService;
    private final PortfolioHeatService portfolioHeatService;
    private final SettingsService settingsService;
    private final TradeRepository tradeRepository;

    @Override
    public PortfolioSnapshot snapshot(PipelineRequest request) {
        Long userId = request.userId();
        if (userId == null) {
            return new PortfolioSnapshot(BigDecimal.ZERO, 0.0, Map.of(), List.of());
        }
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        BigDecimal equity = BigDecimal.valueOf(portfolioService.getAvailableEquity(isPaper, userId));
        double heat = portfolioHeatService.currentPortfolioHeat(userId, equity);
        List<String> openSymbols = tradeRepository.findByUserIdAndStatus(userId, com.apex.backend.model.Trade.TradeStatus.OPEN)
                .stream()
                .map(com.apex.backend.model.Trade::getSymbol)
                .distinct()
                .toList();
        return new PortfolioSnapshot(equity, heat, Map.of(), openSymbols);
    }
}
