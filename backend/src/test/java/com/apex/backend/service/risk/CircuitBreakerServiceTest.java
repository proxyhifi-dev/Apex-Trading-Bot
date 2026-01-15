package com.apex.backend.service.risk;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.entity.TradingGuardState;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.repository.TradingGuardStateRepository;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CircuitBreakerServiceTest {

    @Test
    void triggersCooldownAfterConsecutiveLosses() {
        AdvancedTradingProperties props = new AdvancedTradingProperties();
        props.getRisk().getCircuitBreakers().setEnabled(true);
        props.getRisk().getCircuitBreakers().setMaxConsecutiveLosses(2);
        props.getRisk().getCircuitBreakers().setCooldownMinutes(45);

        TradingGuardStateRepository repo = inMemoryRepo();
        SettingsService settingsService = mock(SettingsService.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        StrategyConfig config = new StrategyConfig();
        when(settingsService.isPaperModeForUser(1L)).thenReturn(true);
        when(paperTradingService.getAccount(1L)).thenReturn(PaperAccount.builder()
                .userId(1L)
                .cashBalance(MoneyUtils.bd(100000))
                .unrealizedPnl(MoneyUtils.ZERO)
                .build());

        CircuitBreakerService service = new CircuitBreakerService(props, repo, settingsService, paperTradingService, config);
        Instant now = Instant.now();
        service.onTradeClosed(1L, BigDecimal.valueOf(-1000), now);
        service.onTradeClosed(1L, BigDecimal.valueOf(-1000), now.plusSeconds(60));

        CircuitBreakerService.GuardDecision decision = service.canTrade(1L, now.plusSeconds(120));
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void resetsOnWin() {
        AdvancedTradingProperties props = new AdvancedTradingProperties();
        props.getRisk().getCircuitBreakers().setEnabled(true);
        props.getRisk().getCircuitBreakers().setMaxConsecutiveLosses(2);

        TradingGuardStateRepository repo = inMemoryRepo();
        SettingsService settingsService = mock(SettingsService.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        StrategyConfig config = new StrategyConfig();
        when(settingsService.isPaperModeForUser(1L)).thenReturn(true);
        when(paperTradingService.getAccount(1L)).thenReturn(PaperAccount.builder()
                .userId(1L)
                .cashBalance(MoneyUtils.bd(100000))
                .unrealizedPnl(MoneyUtils.ZERO)
                .build());

        CircuitBreakerService service = new CircuitBreakerService(props, repo, settingsService, paperTradingService, config);
        Instant now = Instant.now();
        service.onTradeClosed(1L, BigDecimal.valueOf(-500), now);
        service.onTradeClosed(1L, BigDecimal.valueOf(500), now.plusSeconds(60));

        CircuitBreakerService.GuardDecision decision = service.canTrade(1L, now.plusSeconds(120));
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void blocksWhenDailyLossExceeded() {
        AdvancedTradingProperties props = new AdvancedTradingProperties();
        props.getRisk().getCircuitBreakers().setEnabled(true);
        props.getRisk().setMaxDailyLossPct(0.02);

        TradingGuardStateRepository repo = inMemoryRepo();
        SettingsService settingsService = mock(SettingsService.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        StrategyConfig config = new StrategyConfig();
        when(settingsService.isPaperModeForUser(1L)).thenReturn(true);
        when(paperTradingService.getAccount(1L)).thenReturn(PaperAccount.builder()
                .userId(1L)
                .cashBalance(MoneyUtils.bd(100000))
                .unrealizedPnl(MoneyUtils.ZERO)
                .build());

        CircuitBreakerService service = new CircuitBreakerService(props, repo, settingsService, paperTradingService, config);
        Instant now = Instant.now();
        service.onTradeClosed(1L, BigDecimal.valueOf(-3000), now);

        CircuitBreakerService.GuardDecision decision = service.canTrade(1L, now.plusSeconds(120));
        assertThat(decision.allowed()).isFalse();
    }

    private TradingGuardStateRepository inMemoryRepo() {
        TradingGuardStateRepository repo = mock(TradingGuardStateRepository.class);
        AtomicReference<TradingGuardState> stateRef = new AtomicReference<>();
        when(repo.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
        when(repo.save(any())).thenAnswer(invocation -> {
            TradingGuardState state = invocation.getArgument(0);
            stateRef.set(state);
            return state;
        });
        return repo;
    }
}
