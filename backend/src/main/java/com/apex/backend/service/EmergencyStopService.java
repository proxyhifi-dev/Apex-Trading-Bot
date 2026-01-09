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
    private final CircuitBreaker circuitBreaker;
    private final PaperTradingService paperTradingService;

    public EmergencyStopResult triggerEmergencyStop(String reason) {
        int closedTrades = closeAllOpenTrades();
        circuitBreaker.triggerGlobalHalt(reason);
        return new EmergencyStopResult(closedTrades, circuitBreaker.isGlobalHalt());
    }

    private int closeAllOpenTrades() {
        List<Trade> openTrades = tradeRepository.findByStatus(Trade.TradeStatus.OPEN);
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
        openTrades.stream()
                .filter(Trade::isPaperTrade)
                .forEach(trade -> paperTradingService.recordExit(trade.getUserId(), trade));
        return openTrades.size();
    }

    public record EmergencyStopResult(int closedTrades, boolean globalHaltEnabled) {}
}
