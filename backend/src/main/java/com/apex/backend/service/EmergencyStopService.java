package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyStopService {

    private final TradeRepository tradeRepository;
    private final CircuitBreakerService circuitBreakerService;
    private final PaperTradingService paperTradingService;

    public EmergencyStopResult triggerEmergencyStop(Long userId, boolean isPaper, String reason) {
        int closedTrades = closeOpenTrades(userId, isPaper, reason);
        return new EmergencyStopResult(closedTrades, circuitBreakerService.isGlobalHalt());
    }

    private int closeOpenTrades(Long userId, boolean isPaper, String reason) {
        if (userId == null) {
            return 0;
        }
        log.warn("Emergency stop requested for user {} (mode: {}) reason={}", userId, isPaper ? "PAPER" : "LIVE", reason);
        List<Trade> openTrades = tradeRepository.findByUserIdAndIsPaperTradeAndStatus(
                userId,
                isPaper,
                Trade.TradeStatus.OPEN
        );
        LocalDateTime now = LocalDateTime.now();

        for (Trade trade : openTrades) {
            trade.setStatus(Trade.TradeStatus.CLOSED);
            trade.setExitReason(Trade.ExitReason.MANUAL);
            trade.setExitTime(now);
            if (trade.getExitPrice() == null) {
                trade.setExitPrice(trade.getEntryPrice());
            }
            if (trade.getRealizedPnl() == null) {
                trade.setRealizedPnl(BigDecimal.ZERO);
            }
        }

        tradeRepository.saveAll(openTrades);
        if (isPaper) {
            openTrades.forEach(trade -> paperTradingService.recordExit(userId, trade));
        }
        return openTrades.size();
    }

    public record EmergencyStopResult(int closedTrades, boolean globalHaltEnabled) {}
}
