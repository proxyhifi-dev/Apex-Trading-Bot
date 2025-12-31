package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutionService {

    private final TradeRepository tradeRepository;
    private final PortfolioService portfolioService;

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    public void executeTrade(Trade trade) {
        try {
            log.info("Executing trade: {} {} @ {}", trade.getTradeType(), trade.getSymbol(), trade.getEntryPrice());

            boolean isPaper = trade.isPaperTrade();
            double equity = portfolioService.getAvailableEquity(isPaper);

            if (equity < (trade.getQuantity() * trade.getEntryPrice())) {
                log.warn("Insufficient equity to execute trade");
                return;
            }

            trade.setEntryTime(LocalDateTime.now());
            trade.setStatus(Trade.TradeStatus.OPEN);
            tradeRepository.save(trade);

            log.info("Trade executed successfully: {}", trade.getId());
        } catch (Exception e) {
            log.error("Failed to execute trade", e);
        }
    }

    public void closeTrade(Long tradeId, double exitPrice, Trade.ExitReason exitReason) {
        try {
            log.info("Closing trade {} at {}", tradeId, exitPrice);

            Trade trade = tradeRepository.findById(tradeId).orElse(null);
            if (trade != null) {
                trade.setExitPrice(exitPrice);
                trade.setExitTime(LocalDateTime.now());
                trade.setStatus(Trade.TradeStatus.CLOSED);
                trade.setExitReason(exitReason);

                double pnl = (trade.getExitPrice() - trade.getEntryPrice()) * trade.getQuantity();
                if (trade.getTradeType() == Trade.TradeType.SHORT) {
                    pnl = -pnl;
                }
                trade.setRealizedPnl(pnl);

                tradeRepository.save(trade);
                log.info("Trade closed successfully. P&L: {}", pnl);
            }
        } catch (Exception e) {
            log.error("Failed to close trade", e);
        }

    
        /**
     * Execute an automated trade based on signal decision
     */
    public void executeAutoTrade(Object decision, boolean paperTrade, double vixLevel) {
        try {
            log.info("Executing auto trade from signal with VIX: {}", vixLevel);
            log.info("Trade execution type: {}", paperTrade ? "PAPER" : "LIVE");
        } catch (Exception e) {
            log.error("Failed to execute auto trade", e);
        }
            }
}
