package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.util.MoneyUtils;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitManager {

    private final TradeRepository tradeRepository;
    private final PaperTradingService paperTradingService;
    private final FyersService fyersService;
    private final StrategyProperties strategyProperties;
    private final RiskManagementEngine riskManagementEngine;

    public long getOpenTradeCount(Long userId) {
        try {
            if (userId == null) {
                return 0;
            }
            return tradeRepository.countByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
        } catch (Exception e) {
            log.error("Failed to get open trade count", e);
            return 0;
        }
    }

    public boolean isMaxPositionsReached(Long userId, int maxPositions) {
        try {
            return getOpenTradeCount(userId) >= maxPositions;
        } catch (Exception e) {
            log.error("Failed to check max positions", e);
            return false;
        }
    }

    public void closeTradeByTarget(Long tradeId, BigDecimal exitPrice) {
        try {
            log.info("Closing trade {} by target at price {}", tradeId, exitPrice);
            Trade trade = tradeRepository.findById(tradeId).orElse(null);
            if (trade != null) {
                finalizeTrade(trade, exitPrice, Trade.ExitReason.TARGET);
            }
        } catch (Exception e) {
            log.error("Failed to close trade by target", e);
        }
    }

    public void closeTradeByStopLoss(Long tradeId, BigDecimal stopLossPrice) {
        try {
            log.info("Closing trade {} by stop loss at price {}", tradeId, stopLossPrice);
            Trade trade = tradeRepository.findById(tradeId).orElse(null);
            if (trade != null) {
                finalizeTrade(trade, stopLossPrice, Trade.ExitReason.STOP_LOSS);
            }
        } catch (Exception e) {
            log.error("Failed to close trade by stop loss", e);
        }
    }

    public void updateStopLoss(Long tradeId, BigDecimal newStopLoss) {
        try {
            log.info("Updating stop loss for trade {} to {}", tradeId, newStopLoss);
            Trade trade = tradeRepository.findById(tradeId).orElse(null);
            if (trade != null) {
                trade.setCurrentStopLoss(newStopLoss);
                if (trade.getStopLoss() == null) {
                    trade.setStopLoss(newStopLoss);
                }
                tradeRepository.save(trade);
                log.info("Stop loss updated for trade {}", tradeId);
            }
        } catch (Exception e) {
            log.error("Failed to update stop loss", e);
        }
    }

    // NEW: Added missing method
    public void manageExits(Long userId) {
        try {
            log.info("Managing trade exits");
            if (userId == null) {
                return;
            }
            List<Trade> openTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);

            if (openTrades.isEmpty()) {
                return;
            }

            List<String> symbols = openTrades.stream()
                    .map(Trade::getSymbol)
                    .distinct()
                    .toList();
            Map<String, BigDecimal> ltpMap = fyersService.getLtpBatch(symbols);

            for (Trade trade : openTrades) {
                BigDecimal currentPrice = ltpMap.get(trade.getSymbol());
                if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("Skipping trade {} due to missing price data", trade.getId());
                    continue;
                }

                normalizeStops(trade);
                updateTrailingStop(trade, currentPrice);

                boolean stopLossHit = isStopLossHit(trade, currentPrice);
                boolean targetHit = isTargetHit(trade, currentPrice, strategyProperties.getAtr().getTargetMultiplier());
                boolean timeExit = isTimeExit(trade);

                if (stopLossHit) {
                    if (executeExitOrder(trade)) {
                        finalizeTrade(trade, currentPrice, Trade.ExitReason.STOP_LOSS);
                    }
                    continue;
                }

                if (targetHit) {
                    if (executeExitOrder(trade)) {
                        finalizeTrade(trade, currentPrice, Trade.ExitReason.TARGET);
                    }
                    continue;
                }

                if (timeExit) {
                    if (executeExitOrder(trade)) {
                        finalizeTrade(trade, currentPrice, Trade.ExitReason.TIME_EXIT);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to manage exits", e);
        }
    }

    public boolean isStopLossHit(Trade trade, BigDecimal currentPrice) {
        if (trade == null || trade.getCurrentStopLoss() == null) {
            return false;
        }

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            return currentPrice.compareTo(trade.getCurrentStopLoss()) <= 0;
        } else {
            return currentPrice.compareTo(trade.getCurrentStopLoss()) >= 0;
        }
    }

    public boolean isTargetHit(Trade trade, BigDecimal currentPrice, double targetMultiplier) {
        if (trade == null || trade.getAtr() == null) {
            return false;
        }

        BigDecimal target = trade.getEntryPrice().add(trade.getAtr().multiply(BigDecimal.valueOf(targetMultiplier)));

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            return currentPrice.compareTo(target) >= 0;
        } else {
            target = trade.getEntryPrice().subtract(trade.getAtr().multiply(BigDecimal.valueOf(targetMultiplier)));
            return currentPrice.compareTo(target) <= 0;
        }
    }

    private void normalizeStops(Trade trade) {
        if (trade.getCurrentStopLoss() == null && trade.getStopLoss() != null) {
            trade.setCurrentStopLoss(trade.getStopLoss());
        }
        if (trade.getHighestPrice() == null && trade.getEntryPrice() != null) {
            trade.setHighestPrice(trade.getEntryPrice());
        }
    }

    private void updateTrailingStop(Trade trade, BigDecimal currentPrice) {
        if (trade.getStopLoss() == null || trade.getEntryPrice() == null) {
            return;
        }
        boolean updated = false;
        BigDecimal entryPrice = trade.getEntryPrice();
        BigDecimal initialRisk = entryPrice.subtract(trade.getStopLoss()).abs();
        if (initialRisk.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal profit = trade.getTradeType() == Trade.TradeType.LONG
                ? currentPrice.subtract(entryPrice)
                : entryPrice.subtract(currentPrice);
        BigDecimal profitInR = profit.divide(initialRisk, MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP);

        double breakevenMoveR = strategyProperties.getExit().getBreakevenThreshold();
        double breakevenOffsetR = 0.0;
        double trailingStartR = strategyProperties.getExit().getTrailingThreshold();
        double trailingAtrMultiplier = strategyProperties.getExit().getTrailingMultiplier();

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            if (trade.getHighestPrice() == null || currentPrice.compareTo(trade.getHighestPrice()) > 0) {
                trade.setHighestPrice(currentPrice);
                updated = true;
            }
        } else {
            if (trade.getHighestPrice() == null || currentPrice.compareTo(trade.getHighestPrice()) < 0) {
                trade.setHighestPrice(currentPrice);
                updated = true;
            }
        }

        if (!trade.isBreakevenMoved() && profitInR.compareTo(BigDecimal.valueOf(breakevenMoveR)) >= 0) {
            BigDecimal breakevenStop = trade.getTradeType() == Trade.TradeType.LONG
                    ? entryPrice.add(initialRisk.multiply(BigDecimal.valueOf(breakevenOffsetR)))
                    : entryPrice.subtract(initialRisk.multiply(BigDecimal.valueOf(breakevenOffsetR)));
            trade.setCurrentStopLoss(breakevenStop);
            trade.setBreakevenMoved(true);
            updated = true;
        }

        if (profitInR.compareTo(BigDecimal.valueOf(trailingStartR)) >= 0 && trade.getAtr() != null) {
            BigDecimal trailDistance = trade.getAtr().multiply(BigDecimal.valueOf(trailingAtrMultiplier));
            if (trade.getTradeType() == Trade.TradeType.LONG) {
                BigDecimal proposedStop = trade.getHighestPrice().subtract(trailDistance);
                if (trade.getCurrentStopLoss() == null || proposedStop.compareTo(trade.getCurrentStopLoss()) > 0) {
                    trade.setCurrentStopLoss(proposedStop);
                    updated = true;
                }
            } else {
                BigDecimal proposedStop = trade.getHighestPrice().add(trailDistance);
                if (trade.getCurrentStopLoss() == null || proposedStop.compareTo(trade.getCurrentStopLoss()) < 0) {
                    trade.setCurrentStopLoss(proposedStop);
                    updated = true;
                }
            }
        }

        if (updated) {
            tradeRepository.save(trade);
        }
    }

    private boolean isTimeExit(Trade trade) {
        if (trade.getEntryTime() == null) {
            return false;
        }
        long minutes = Duration.between(trade.getEntryTime(), LocalDateTime.now()).toMinutes();
        long bars = minutes / 5;
        int timeStopBars = strategyProperties.getExit().getTimeStopBars();
        int maxBars = strategyProperties.getExit().getMaxBars();
        return bars >= maxBars || bars >= timeStopBars;
    }

    private boolean executeExitOrder(Trade trade) {
        if (trade.isPaperTrade()) {
            return true;
        }
        String side = trade.getTradeType() == Trade.TradeType.LONG ? "SELL" : "BUY";
        try {
            String orderId = fyersService.placeOrder(trade.getSymbol(), trade.getQuantity(), side, "MARKET", 0);
            log.info("Exit order placed for trade {}: {}", trade.getId(), orderId);
            return true;
        } catch (Exception e) {
            log.error("Failed to place exit order for trade {}", trade.getId(), e);
            return false;
        }
    }

    private void finalizeTrade(Trade trade, BigDecimal exitPrice, Trade.ExitReason reason) {
        trade.setExitPrice(exitPrice);
        trade.setExitTime(LocalDateTime.now());
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitReason(reason);
        BigDecimal pnl = MoneyUtils.multiply(exitPrice.subtract(trade.getEntryPrice()), trade.getQuantity());
        if (trade.getTradeType() == Trade.TradeType.SHORT) {
            pnl = pnl.negate();
        }
        trade.setRealizedPnl(MoneyUtils.scale(pnl));
        tradeRepository.save(trade);
        riskManagementEngine.removeOpenPosition(trade.getSymbol());
        riskManagementEngine.updateDailyLoss(pnl.doubleValue());
        if (trade.isPaperTrade()) {
            paperTradingService.recordExit(trade.getUserId(), trade);
        }
        log.info("Trade {} closed successfully. P&L: {}", trade.getId(), pnl);
    }
}
