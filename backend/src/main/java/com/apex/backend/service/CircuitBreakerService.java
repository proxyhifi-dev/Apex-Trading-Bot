package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.CircuitBreakerLog;
import com.apex.backend.model.CircuitBreakerState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.CircuitBreakerLogRepository;
import com.apex.backend.repository.CircuitBreakerStateRepository;
import com.apex.backend.repository.TradeRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final StrategyConfig strategyConfig;
    private final StrategyProperties strategyProperties;
    private final CircuitBreakerLogRepository logRepository;
    private final TradeRepository tradeRepository;
    private final BroadcastService broadcastService;
    private final CircuitBreakerStateRepository stateRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;

    @Getter
    private boolean globalHalt = false;
    @Getter
    private boolean entryHalt = false;
    private LocalDateTime pauseUntil = null;
    private int consecutiveLosses = 0;

    @jakarta.annotation.PostConstruct
    public void loadState() {
        CircuitBreakerState state = stateRepository.findById(1L).orElseGet(() -> CircuitBreakerState.builder()
                .id(1L)
                .globalHalt(false)
                .entryHalt(false)
                .consecutiveLosses(0)
                .updatedAt(LocalDateTime.now())
                .build());
        applyState(state);
        stateRepository.save(state);
    }

    public boolean canTrade() {
        if (globalHalt) {
            return false;
        }
        if (pauseUntil != null) {
            if (LocalDateTime.now().isBefore(pauseUntil)) {
                return false;
            }
            pauseUntil = null;
        }
        return !entryHalt;
    }

    public void updateAfterTrade(double tradeResult) {
        if (tradeResult < 0) {
            consecutiveLosses++;
        } else {
            consecutiveLosses = 0;
        }
        metricsService.updatePnl(tradeResult);
        persistState();
        updateMetrics();
        if (strategyProperties.getCircuit().isAutoPause() && consecutiveLosses >= strategyProperties.getCircuit().getMaxConsecutiveLosses()) {
            pauseTrading("MAX_CONSECUTIVE_LOSSES", null);
        }
    }

    public void updateMetrics() {
        Long ownerUserId = strategyConfig.getTrading().getOwnerUserId();
        if (ownerUserId == null) {
            return;
        }
        PnlSummary pnlSummary = calculatePnl(ownerUserId);
        StrategyProperties.Circuit circuit = strategyProperties.getCircuit();

        if (circuit.isAutoPause() && pnlSummary.dailyPnl <= -(circuit.getDailyLossLimit() * pnlSummary.startingCapital)) {
            pauseTrading("DAILY_LIMIT", BigDecimal.valueOf(pnlSummary.dailyPnl));
        }
        if (circuit.isAutoPause() && pnlSummary.weeklyPnl <= -(circuit.getWeeklyLossLimit() * pnlSummary.startingCapital)) {
            pauseTrading("WEEKLY_LIMIT", BigDecimal.valueOf(pnlSummary.weeklyPnl));
        }
        if (circuit.isAutoPause() && pnlSummary.monthlyPnl <= -(circuit.getMonthlyLossLimit() * pnlSummary.startingCapital)) {
            pauseTrading("MONTHLY_LIMIT", BigDecimal.valueOf(pnlSummary.monthlyPnl));
        }
    }

    public void triggerGlobalHalt(String reason) {
        if (!globalHalt) {
            globalHalt = true;
            log.error("⛔ Global halt triggered: {}", reason);
            logRepository.save(CircuitBreakerLog.builder()
                    .triggerTime(LocalDateTime.now())
                    .reason(reason)
                    .triggeredValue(null)
                    .actionTaken("HALT_ALL").build());
            broadcastPauseEvent(reason);
            alertService.sendAlert("KILL_SWITCH", reason);
            persistState();
        }
    }

    public void pauseTrading(String reason, BigDecimal triggeredValue) {
        if (!entryHalt) {
            entryHalt = true;
            log.error("⛔ Circuit breaker pause: {}", reason);
            logRepository.save(CircuitBreakerLog.builder()
                    .triggerTime(LocalDateTime.now())
                    .reason(reason)
                    .triggeredValue(triggeredValue)
                    .actionTaken("HALT_ENTRIES").build());
            broadcastPauseEvent(reason);
            alertService.sendAlert("CIRCUIT_BREAKER", reason);
            persistState();
        }
    }

    private void broadcastPauseEvent(String reason) {
        broadcastService.broadcastBotStatus(new CircuitPauseEvent(true, reason, LocalDateTime.now()));
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDaily() {
        entryHalt = false;
        consecutiveLosses = 0;
        log.info("🔄 Daily Circuit Breaker Reset.");
        persistState();
    }

    private PnlSummary calculatePnl(Long userId) {
        List<Trade> trades = tradeRepository.findByUserId(userId).stream()
                .filter(trade -> trade.getExitTime() != null && trade.getStatus() == Trade.TradeStatus.CLOSED)
                .toList();
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int currentWeek = today.get(weekFields.weekOfWeekBasedYear());
        int currentYear = today.getYear();
        double dailyPnl = trades.stream()
                .filter(trade -> trade.getExitTime().toLocalDate().isEqual(today))
                .mapToDouble(trade -> trade.getRealizedPnl() != null ? trade.getRealizedPnl().doubleValue() : 0.0)
                .sum();
        double weeklyPnl = trades.stream()
                .filter(trade -> {
                    LocalDate exitDate = trade.getExitTime().toLocalDate();
                    return exitDate.get(weekFields.weekOfWeekBasedYear()) == currentWeek && exitDate.getYear() == currentYear;
                })
                .mapToDouble(trade -> trade.getRealizedPnl() != null ? trade.getRealizedPnl().doubleValue() : 0.0)
                .sum();
        double monthlyPnl = trades.stream()
                .filter(trade -> trade.getExitTime().getMonth() == today.getMonth() && trade.getExitTime().getYear() == currentYear)
                .mapToDouble(trade -> trade.getRealizedPnl() != null ? trade.getRealizedPnl().doubleValue() : 0.0)
                .sum();
        double capital = strategyConfig.getTrading().getCapital();
        return new PnlSummary(dailyPnl, weeklyPnl, monthlyPnl, capital);
    }

    private record PnlSummary(double dailyPnl, double weeklyPnl, double monthlyPnl, double startingCapital) {}

    public record CircuitPauseEvent(boolean paused, String reason, LocalDateTime timestamp) {}

    private void persistState() {
        CircuitBreakerState state = CircuitBreakerState.builder()
                .id(1L)
                .globalHalt(globalHalt)
                .entryHalt(entryHalt)
                .pauseUntil(pauseUntil)
                .reason(entryHalt || globalHalt ? "ACTIVE" : "OK")
                .consecutiveLosses(consecutiveLosses)
                .updatedAt(LocalDateTime.now())
                .build();
        stateRepository.save(state);
    }

    private void applyState(CircuitBreakerState state) {
        this.globalHalt = state.isGlobalHalt();
        this.entryHalt = state.isEntryHalt();
        this.pauseUntil = state.getPauseUntil();
        this.consecutiveLosses = state.getConsecutiveLosses();
    }
}
