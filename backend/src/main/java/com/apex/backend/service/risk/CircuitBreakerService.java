package com.apex.backend.service.risk;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.entity.TradingGuardState;
import com.apex.backend.repository.TradingGuardStateRepository;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final AdvancedTradingProperties advancedTradingProperties;
    private final TradingGuardStateRepository tradingGuardStateRepository;
    private final SettingsService settingsService;
    private final PaperTradingService paperTradingService;
    private final StrategyConfig strategyConfig;
    private final com.apex.backend.service.RiskEventService riskEventService;

    public record GuardDecision(boolean allowed, String reason, Instant until) {}

    @Transactional
    public GuardDecision canTrade(Long userId, Instant nowUtc) {
        AdvancedTradingProperties.CircuitBreakers cfg = advancedTradingProperties.getRisk().getCircuitBreakers();
        if (!cfg.isEnabled()) {
            return new GuardDecision(true, "Circuit breaker disabled", null);
        }
        TradingGuardState state = loadState(userId, nowUtc);
        if (state.getCooldownUntil() != null && nowUtc.isBefore(state.getCooldownUntil())) {
            return new GuardDecision(false, "Cooldown active", state.getCooldownUntil());
        }

        BigDecimal equity = resolveEquity(userId);
        if (equity.compareTo(BigDecimal.ZERO) <= 0) {
            return new GuardDecision(true, "Equity unavailable", null);
        }
        BigDecimal maxLoss = equity.multiply(BigDecimal.valueOf(advancedTradingProperties.getRisk().getMaxDailyLossPct())).negate();
        BigDecimal dayPnl = state.getDayPnl() != null ? state.getDayPnl() : MoneyUtils.ZERO;
        if (dayPnl.compareTo(maxLoss) <= 0) {
            Instant endOfDay = endOfTradingDay(nowUtc);
            riskEventService.record(userId, "CIRCUIT_DAILY_LOSS", "Daily loss limit reached", "dayPnl=" + dayPnl);
            return new GuardDecision(false, "Daily loss limit reached", endOfDay);
        }
        if (state.getConsecutiveLosses() >= cfg.getMaxConsecutiveLosses()) {
            Instant until = state.getCooldownUntil() != null ? state.getCooldownUntil() : nowUtc;
            riskEventService.record(userId, "CIRCUIT_CONSECUTIVE_LOSS", "Max consecutive losses", "count=" + state.getConsecutiveLosses());
            return new GuardDecision(false, "Max consecutive losses", until);
        }
        return new GuardDecision(true, "Allowed", null);
    }

    @Transactional
    public void onTradeClosed(Long userId, BigDecimal realizedPnl, Instant closedAtUtc) {
        AdvancedTradingProperties.CircuitBreakers cfg = advancedTradingProperties.getRisk().getCircuitBreakers();
        if (!cfg.isEnabled() || userId == null) {
            return;
        }
        TradingGuardState state = loadState(userId, closedAtUtc);
        BigDecimal pnl = realizedPnl != null ? realizedPnl : MoneyUtils.ZERO;
        state.setDayPnl(MoneyUtils.add(state.getDayPnl(), pnl));
        if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            state.setConsecutiveLosses(state.getConsecutiveLosses() + 1);
            state.setLastLossAt(closedAtUtc);
        } else if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            state.setConsecutiveLosses(0);
        }
        if (state.getConsecutiveLosses() >= cfg.getMaxConsecutiveLosses()) {
            Instant cooldownUntil = closedAtUtc.plusSeconds(cfg.getCooldownMinutes() * 60L);
            state.setCooldownUntil(cooldownUntil);
        }
        state.setUpdatedAt(Instant.now());
        tradingGuardStateRepository.save(state);
    }

    private TradingGuardState loadState(Long userId, Instant nowUtc) {
        TradingGuardState state = tradingGuardStateRepository.findById(userId)
                .orElseGet(() -> TradingGuardState.builder()
                        .userId(userId)
                        .consecutiveLosses(0)
                        .dayPnl(MoneyUtils.ZERO)
                        .tradingDayDate(tradingDay(nowUtc))
                        .updatedAt(Instant.now())
                        .build());
        LocalDate today = tradingDay(nowUtc);
        if (state.getTradingDayDate() == null || !state.getTradingDayDate().equals(today)) {
            state.setTradingDayDate(today);
            state.setDayPnl(MoneyUtils.ZERO);
            state.setConsecutiveLosses(0);
            state.setCooldownUntil(null);
            state.setUpdatedAt(Instant.now());
            tradingGuardStateRepository.save(state);
        }
        return state;
    }

    private LocalDate tradingDay(Instant nowUtc) {
        return nowUtc.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
    }

    private Instant endOfTradingDay(Instant nowUtc) {
        ZonedDateTime zoned = nowUtc.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Kolkata"));
        return zoned.toInstant();
    }

    private BigDecimal resolveEquity(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        if (settingsService.isPaperModeForUser(userId)) {
            var account = paperTradingService.getAccount(userId);
            if (account.getCashBalance() != null) {
                return MoneyUtils.add(account.getCashBalance(), account.getUnrealizedPnl());
            }
            return MoneyUtils.ZERO;
        }
        double capital = strategyConfig.getTrading().getCapital();
        if (capital <= 0) {
            log.warn("Live equity not configured; using fallback capital for circuit breaker");
            capital = 100_000.0;
        }
        return MoneyUtils.bd(capital);
    }
}
