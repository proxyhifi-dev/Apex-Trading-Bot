package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TradeRepository tradeRepository;
    private final PaperTradingService paperTradingService;
    private final StrategyConfig strategyConfig;

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    @Deprecated
    public double getPortfolioValue(boolean isPaper) {
        log.warn("Deprecated portfolio value lookup without user id. Provide userId for user-scoped calls.");
        return getPortfolioValue(isPaper, resolveOwnerUserId());
    }

    public double getPortfolioValue(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                if (userId == null) {
                    return 0.0;
                }
                PaperAccount account = resolvePaperAccount(userId);
                BigDecimal positionValue = paperTradingService.getOpenPositionsMarketValue(userId);
                BigDecimal total = MoneyUtils.add(account.getCashBalance(), positionValue);
                return total.doubleValue();
            }
            if (userId == null) {
                return initialCapital;
            }
            BigDecimal totalPnl = tradeRepository.getTotalPnlByUserAndMode(userId, isPaper);
            BigDecimal pnl = (totalPnl != null) ? totalPnl : MoneyUtils.ZERO;
            return initialCapital + pnl.doubleValue();
        } catch (Exception e) {
            log.error("Failed to calculate portfolio value", e);
            return initialCapital;
        }
    }

    @Deprecated
    public double getAvailableCash(boolean isPaper) {
        log.warn("Deprecated available cash lookup without user id. Provide userId for user-scoped calls.");
        return getAvailableCash(isPaper, resolveOwnerUserId());
    }

    public double getAvailableCash(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                PaperAccount account = resolvePaperAccount(userId);
                return account != null ? account.getCashBalance().doubleValue() : 0.0;
            }
            if (userId == null) {
                return initialCapital;
            }
            List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(userId, isPaper, Trade.TradeStatus.OPEN);
            double usedCapital = openTrades.stream()
                    .mapToDouble(t -> t.getQuantity() * t.getEntryPrice().doubleValue())
                    .sum();
            return initialCapital - usedCapital;
        } catch (Exception e) {
            log.error("Failed to calculate available cash", e);
            return initialCapital;
        }
    }

    // NEW: Added missing method
    @Deprecated
    public double getAvailableEquity(boolean isPaper) {
        log.warn("Deprecated available equity lookup without user id. Provide userId for user-scoped calls.");
        return getAvailableEquity(isPaper, resolveOwnerUserId());
    }

    public double getAvailableEquity(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                PaperAccount account = resolvePaperAccount(userId);
                if (account == null) {
                    return 0.0;
                }
                BigDecimal positionValue = paperTradingService.getOpenPositionsMarketValue(userId);
                return MoneyUtils.add(account.getCashBalance(), positionValue).doubleValue();
            }
            return getAvailableCash(isPaper, userId);
        } catch (Exception e) {
            log.error("Failed to get available equity", e);
            return initialCapital;
        }
    }

    public double getTotalInvested(boolean isPaper) {
        return getTotalInvested(isPaper, resolveOwnerUserId());
    }

    public double getTotalInvested(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                if (userId == null) {
                    return 0.0;
                }
                return paperTradingService.getOpenPositionsMarketValue(userId).doubleValue();
            }
            if (userId == null) {
                return 0;
            }
            List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(userId, isPaper, Trade.TradeStatus.OPEN);
            return openTrades.stream()
                    .mapToDouble(t -> t.getQuantity() * t.getEntryPrice().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.error("Failed to calculate total invested", e);
            return 0;
        }
    }

    public double getRealizedPnL(boolean isPaper) {
        return getRealizedPnL(isPaper, resolveOwnerUserId());
    }

    public double getRealizedPnL(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                PaperAccount account = resolvePaperAccount(userId);
                return account != null ? account.getRealizedPnl().doubleValue() : 0.0;
            }
            if (userId == null) {
                return 0;
            }
            BigDecimal totalPnl = tradeRepository.getTotalPnlByUserAndMode(userId, isPaper);
            return (totalPnl != null) ? totalPnl.doubleValue() : 0;
        } catch (Exception e) {
            log.error("Failed to get realized P&L", e);
            return 0;
        }
    }

    public int getOpenPositionCount(boolean isPaper) {
        return getOpenPositionCount(isPaper, resolveOwnerUserId());
    }

    public int getOpenPositionCount(boolean isPaper, Long userId) {
        try {
            if (isPaper) {
                if (userId == null) {
                    return 0;
                }
                return paperTradingService.getOpenPositions(userId).size();
            }
            if (userId == null) {
                return 0;
            }
            List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(userId, isPaper, Trade.TradeStatus.OPEN);
            return openTrades.size();
        } catch (Exception e) {
            log.error("Failed to get open position count", e);
            return 0;
        }
    }

    /**
     * Build price series for symbols currently in the portfolio.
     * Uses trade entry/exit prices as historical points.
     */
    @Deprecated
    public Map<String, List<Double>> getPortfolioPriceSeries(boolean isPaper, int maxPoints) {
        log.warn("Deprecated portfolio price series lookup without user id. Provide userId for user-scoped calls.");
        return getPortfolioPriceSeries(isPaper, maxPoints, resolveOwnerUserId());
    }

    public Map<String, List<Double>> getPortfolioPriceSeries(boolean isPaper, int maxPoints, Long userId) {
        try {
            if (userId == null) {
                return Map.of();
            }
            List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(userId, isPaper, Trade.TradeStatus.OPEN);
            if (openTrades.isEmpty()) {
                return Map.of();
            }

            Set<String> symbols = new LinkedHashSet<>();
            for (Trade trade : openTrades) {
                symbols.add(trade.getSymbol());
            }

            Map<String, List<Double>> series = new LinkedHashMap<>();
            for (String symbol : symbols) {
                List<Trade> symbolTrades = tradeRepository.findByUserIdAndSymbolAndIsPaperTradeOrderByEntryTimeAsc(
                        userId, symbol, isPaper);
                List<Double> prices = new ArrayList<>();
                for (Trade trade : symbolTrades) {
                    if (trade.getEntryPrice() != null) {
                        prices.add(trade.getEntryPrice().doubleValue());
                    }
                    if (trade.getExitPrice() != null) {
                        prices.add(trade.getExitPrice().doubleValue());
                    }
                }

                if (prices.size() > maxPoints) {
                    prices = prices.subList(prices.size() - maxPoints, prices.size());
                }

                if (!prices.isEmpty()) {
                    series.put(symbol, prices);
                }
            }

            return series;
        } catch (Exception e) {
            log.error("Failed to build portfolio price series", e);
            return Map.of();
        }
    }

    private PaperAccount resolvePaperAccount() {
        return resolvePaperAccount(resolveOwnerUserId());
    }

    private PaperAccount resolvePaperAccount(Long userId) {
        if (userId == null) {
            return null;
        }
        return paperTradingService.getAccount(userId);
    }

    private Long resolveOwnerUserId() {
        return strategyConfig.getTrading().getOwnerUserId();
    }
}
