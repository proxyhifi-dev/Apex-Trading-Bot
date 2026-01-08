package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitManager {

    private final TradeRepository tradeRepository;
    private final PaperTradingService paperTradingService;

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
                trade.setExitPrice(exitPrice);
                trade.setStatus(Trade.TradeStatus.CLOSED);
                trade.setExitReason(Trade.ExitReason.TARGET);
                double pnl = (trade.getExitPrice() - trade.getEntryPrice()) * trade.getQuantity();
                if (trade.getTradeType() == Trade.TradeType.SHORT) {
                    pnl = -pnl;
                }
                trade.setRealizedPnl(pnl);
                tradeRepository.save(trade);
                if (trade.isPaperTrade()) {
                    paperTradingService.recordExit(trade);
                }
                log.info("Trade {} closed successfully. P&L: {}", tradeId, pnl);
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
                trade.setExitPrice(stopLossPrice);
                trade.setStatus(Trade.TradeStatus.CLOSED);
                trade.setExitReason(Trade.ExitReason.STOP_LOSS);
                double pnl = (trade.getExitPrice() - trade.getEntryPrice()) * trade.getQuantity();
                if (trade.getTradeType() == Trade.TradeType.SHORT) {
                    pnl = -pnl;
                }
                trade.setRealizedPnl(pnl);
                tradeRepository.save(trade);
                if (trade.isPaperTrade()) {
                    paperTradingService.recordExit(trade);
                }
                log.info("Trade {} closed by stop loss. P&L: {}", tradeId, pnl);
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

            for (Trade trade : openTrades) {
                // Check if stop loss or target was hit
                // This is a placeholder - implement your exit logic here
                log.debug("Checking exit conditions for trade: {}", trade.getId());
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
}
