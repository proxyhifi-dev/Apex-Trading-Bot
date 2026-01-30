package com.apex.backend.service;

import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeCloseService {

    private final TradeRepository tradeRepository;
    private final RiskManagementEngine riskManagementEngine;
    private final com.apex.backend.service.risk.CircuitBreakerService tradingGuardService;
    private final PaperTradingService paperTradingService;

    public boolean markClosing(Trade trade, String reason) {
        if (trade == null || trade.getStatus() == Trade.TradeStatus.CLOSED) {
            return false;
        }
        if (trade.getPositionState() != PositionState.CLOSING && trade.getPositionState() != PositionState.CLOSED) {
            trade.transitionTo(PositionState.CLOSING);
            trade.setExitReasonDetail(reason);
            tradeRepository.save(trade);
            return true;
        }
        return false;
    }

    public boolean finalizeTrade(Trade trade, BigDecimal exitPrice, Trade.ExitReason reason, String detail) {
        if (trade == null) {
            return false;
        }
        if (trade.getStatus() == Trade.TradeStatus.CLOSED) {
            return true;
        }
        trade.setExitPrice(exitPrice);
        trade.setExitTime(LocalDateTime.now());
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setExitReason(reason);
        trade.setExitReasonDetail(detail);
        trade.transitionTo(PositionState.CLOSED);
        BigDecimal pnl = MoneyUtils.multiply(exitPrice.subtract(trade.getEntryPrice()), trade.getQuantity());
        if (trade.getTradeType() == Trade.TradeType.SHORT) {
            pnl = pnl.negate();
        }
        trade.setRealizedPnl(MoneyUtils.scale(pnl));
        tradeRepository.save(trade);
        riskManagementEngine.removeOpenPosition(trade.getSymbol());
        riskManagementEngine.updateDailyLoss(pnl.doubleValue());
        tradingGuardService.onTradeClosed(trade.getUserId(), trade.getRealizedPnl(), Instant.now());
        if (trade.isPaperTrade()) {
            paperTradingService.recordExit(trade.getUserId(), trade);
        }
        log.info("Trade {} closed successfully. P&L: {}", trade.getId(), pnl);
        return true;
    }
}
