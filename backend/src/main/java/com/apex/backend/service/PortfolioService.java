package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.HoldingDTO;
import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

    private final StrategyConfig strategyConfig;
    private final TradeRepository tradeRepo;
    private final FyersService fyersService;

    public UserProfileDTO getPortfolioSnapshot() {
        // 1. Base Capital
        double initialCapital = strategyConfig.getTrading().getCapital();

        // 2. Realized P&L
        Double realPnL = tradeRepo.getTotalPnlByMode(false); // false = REAL
        Double paperPnL = tradeRepo.getTotalPnlByMode(true); // true = PAPER

        double totalRealPnL = (realPnL != null) ? realPnL : 0.0;
        double totalPaperPnL = (paperPnL != null) ? paperPnL : 0.0;

        // 3. Available Funds
        double availableRealFunds = initialCapital + totalRealPnL;
        double availablePaperFunds = initialCapital + totalPaperPnL;

        // 4. Open Positions
        List<Trade> openLiveTrades = tradeRepo.findByIsPaperTradeAndStatus(false, Trade.TradeStatus.OPEN);
        List<HoldingDTO> holdings = calculateHoldings(openLiveTrades);

        // 5. Aggregate Totals
        double totalInvested = holdings.stream().mapToDouble(h -> h.getAvgPrice() * h.getQuantity()).sum();
        double currentHoldingsValue = holdings.stream().mapToDouble(h -> h.getCurrentPrice() * h.getQuantity()).sum();
        double todaysPnl = currentHoldingsValue - totalInvested;

        return UserProfileDTO.builder()
                .name("Apex Trader")
                .availableFunds(availableRealFunds)      // Legacy
                .availableRealFunds(availableRealFunds)  // Live
                .availablePaperFunds(availablePaperFunds)// Paper
                .totalInvested(totalInvested)
                .currentValue(availableRealFunds + currentHoldingsValue)
                .todaysPnl(todaysPnl)
                .holdings(holdings)
                .build();
    }

    // Helper: Calculate live value of positions
    private List<HoldingDTO> calculateHoldings(List<Trade> trades) {
        return trades.stream().map(t -> {
            double currentLtp = fyersService.getLTP(t.getSymbol());
            if (currentLtp == 0) currentLtp = t.getEntryPrice();

            double currentValue = currentLtp * t.getQuantity();
            double invested = t.getEntryPrice() * t.getQuantity();
            double pnl = currentValue - invested;

            return HoldingDTO.builder()
                    .symbol(t.getSymbol())
                    .quantity(t.getQuantity())
                    .avgPrice(t.getEntryPrice())
                    .currentPrice(currentLtp)
                    .pnl(pnl)
                    .build();
        }).collect(Collectors.toList());
    }

    // ✅ Used by TradeExecutionService
    public double getAvailableEquity(boolean isPaper) {
        double initialCapital = strategyConfig.getTrading().getCapital();
        Double realizedPnL = tradeRepo.getTotalPnlByMode(isPaper);
        return initialCapital + (realizedPnL != null ? realizedPnL : 0.0);
    }
}