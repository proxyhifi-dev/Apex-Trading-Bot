package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.CircuitBreakerLog;
import com.apex.backend.repository.CircuitBreakerLogRepository;
import com.apex.backend.repository.TradeRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CircuitBreaker {

    private final StrategyConfig config;
    private final CircuitBreakerLogRepository logRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioService portfolioService;

    @Getter
    private boolean isGlobalHalt = false;
    @Getter
    private boolean isEntryHalt = false;

    private LocalDateTime pauseUntil = null;

    // ✅ Re-added for backward compatibility
    public boolean isOpen() { return isGlobalHalt; }

    public boolean canTrade() {
        if (isGlobalHalt) return false;
        if (pauseUntil != null) {
            if (LocalDateTime.now().isBefore(pauseUntil)) return false;
            pauseUntil = null;
        }
        return !isEntryHalt;
    }

    public void updateMetrics() {
        // Calculate PnL based on closed trades from today
        double dailyPnl = calculatePnl(LocalDate.now());
        double dailyLimit = -(100000.0 * config.getRisk().getDailyLossLimitPct());

        if (dailyPnl <= dailyLimit) {
            if (!isEntryHalt) {
                isEntryHalt = true;
                log.error("⛔ Daily Limit Hit. Halting Entries.");
                logRepository.save(CircuitBreakerLog.builder()
                        .triggerTime(LocalDateTime.now())
                        .reason("DAILY_LIMIT")
                        .triggeredValue(dailyPnl)
                        .actionTaken("HALT_ENTRIES").build());
            }
        }
    }

    private double calculatePnl(LocalDate since) {
        return tradeRepository.findAll().stream()
                .filter(t -> t.getExitTime() != null && t.getExitTime().toLocalDate().isEqual(since))
                .mapToDouble(t -> t.getRealizedPnl() != null ? t.getRealizedPnl() : 0.0)
                .sum();
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDaily() {
        isEntryHalt = false;
        log.info("🔄 Daily Circuit Breaker Reset.");
    }
}