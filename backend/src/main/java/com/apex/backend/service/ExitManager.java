package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.config.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final StrategyConfig strategyConfig;
    private final RiskManagementEngine riskManagementEngine;

    public long getOpenTradeCount() {
        try {
            return tradeRepository.countByStatus(Trade.TradeStatus.OPEN);
        } catch (Exception e) {
            log.error("Failed to get open trade count", e);
            return 0;
        }
    }

    public boolean isMaxPositionsReached(int maxPositions) {
        try {
            return getOpenTradeCount() >= maxPositions;
        } catch (Exception e) {
            log.error("Failed to check max positions", e);
            return false;
        }
    }

    public void closeTradeByTarget(Long tradeId, double exitPrice) {
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

    public void closeTradeByStopLoss(Long tradeId, double stopLossPrice) {
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

    public void updateStopLoss(Long tradeId, double newStopLoss) {
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
    public void manageExits() {
        try {
            log.info("Managing trade exits");
            List<Trade> openTrades = tradeRepository.findByStatus(Trade.TradeStatus.OPEN);

            if (openTrades.isEmpty()) {
                return;
            }

            List<String> symbols = openTrades.stream()
                    .map(Trade::getSymbol)
                    .distinct()
                    .toList();
            Map<String, Double> ltpMap = fyersService.getLtpBatch(symbols);

            for (Trade trade : openTrades) {
                Double currentPrice = ltpMap.get(trade.getSymbol());
                if (currentPrice == null || currentPrice <= 0) {
                    log.debug("Skipping trade {} due to missing price data", trade.getId());
                    continue;
                }

                normalizeStops(trade);
                updateTrailingStop(trade, currentPrice);

                boolean stopLossHit = isStopLossHit(trade, currentPrice);
                boolean targetHit = isTargetHit(trade, currentPrice, strategyConfig.getRisk().getTargetMultiplier());

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
            }
        } catch (Exception e) {
            log.error("Failed to manage exits", e);
        }
    }

    public boolean isStopLossHit(Trade trade, double currentPrice) {
        if (trade == null || trade.getCurrentStopLoss() == null) {
            return false;
        }

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            return currentPrice <= trade.getCurrentStopLoss();
        } else {
            return currentPrice >= trade.getCurrentStopLoss();
        }
    }

    public boolean isTargetHit(Trade trade, double currentPrice, double targetMultiplier) {
        if (trade == null || trade.getAtr() == null) {
            return false;
        }

        double target = trade.getEntryPrice() + (trade.getAtr() * targetMultiplier);

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            return currentPrice >= target;
        } else {
            target = trade.getEntryPrice() - (trade.getAtr() * targetMultiplier);
            return currentPrice <= target;
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

    private void updateTrailingStop(Trade trade, double currentPrice) {
        if (trade.getStopLoss() == null || trade.getEntryPrice() == null) {
            return;
        }
        boolean updated = false;
        double entryPrice = trade.getEntryPrice();
        double initialRisk = Math.abs(entryPrice - trade.getStopLoss());
        if (initialRisk <= 0) {
            return;
        }
        double profit = trade.getTradeType() == Trade.TradeType.LONG
                ? currentPrice - entryPrice
                : entryPrice - currentPrice;
        double profitInR = profit / initialRisk;

        if (trade.getTradeType() == Trade.TradeType.LONG) {
            if (trade.getHighestPrice() == null || currentPrice > trade.getHighestPrice()) {
                trade.setHighestPrice(currentPrice);
                updated = true;
            }
        } else {
            if (trade.getHighestPrice() == null || currentPrice < trade.getHighestPrice()) {
                trade.setHighestPrice(currentPrice);
                updated = true;
            }
        }

        if (!trade.isBreakevenMoved() && profitInR >= 1.0) {
            double breakevenStop = trade.getTradeType() == Trade.TradeType.LONG
                    ? entryPrice + (initialRisk * 0.1)
                    : entryPrice - (initialRisk * 0.1);
            trade.setCurrentStopLoss(breakevenStop);
            trade.setBreakevenMoved(true);
            updated = true;
        }

        if (profitInR >= 2.0 && trade.getAtr() != null) {
            double trailDistance = trade.getAtr() * 1.5;
            if (trade.getTradeType() == Trade.TradeType.LONG) {
                double proposedStop = trade.getHighestPrice() - trailDistance;
                if (trade.getCurrentStopLoss() == null || proposedStop > trade.getCurrentStopLoss()) {
                    trade.setCurrentStopLoss(proposedStop);
                    updated = true;
                }
            } else {
                double proposedStop = trade.getHighestPrice() + trailDistance;
                if (trade.getCurrentStopLoss() == null || proposedStop < trade.getCurrentStopLoss()) {
                    trade.setCurrentStopLoss(proposedStop);
                    updated = true;
                }
            }
        }

        if (updated) {
            tradeRepository.save(trade);
        }
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

    private void finalizeTrade(Trade trade, double exitPrice, Trade.ExitReason reason) {
        trade.setExitPrice(exitPrice);
        trade.setExitTime(LocalDateTime.now());
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitReason(reason);
        double pnl = (exitPrice - trade.getEntryPrice()) * trade.getQuantity();
        if (trade.getTradeType() == Trade.TradeType.SHORT) {
            pnl = -pnl;
        }
        trade.setRealizedPnl(pnl);
        tradeRepository.save(trade);
        riskManagementEngine.removeOpenPosition(trade.getSymbol());
        if (trade.isPaperTrade()) {
            paperTradingService.recordExit(trade);
        }
        log.info("Trade {} closed successfully. P&L: {}", trade.getId(), pnl);
    }
}
