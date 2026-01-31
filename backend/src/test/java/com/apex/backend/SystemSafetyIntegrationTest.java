package com.apex.backend;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.TradeStateAudit;
import com.apex.backend.model.User;
import com.apex.backend.repository.ExitRetryRepository;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.SystemGuardStateRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.TradeStateAuditRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.EmergencyPanicService;
import com.apex.backend.service.ExecutionEngine;
import com.apex.backend.service.ExitRetryService;
import com.apex.backend.service.RiskManagementEngine;
import com.apex.backend.service.SystemGuardService;
import com.apex.backend.service.TradeCloseService;
import com.apex.backend.service.WatchlistService;
import com.apex.backend.service.risk.FyersBrokerPort;
import com.apex.backend.service.risk.PaperBrokerPort;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.risk.ReconciliationService;
import com.apex.backend.service.StopLossEnforcementService;
import com.apex.backend.service.risk.CircuitBreakerService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.BotScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.admin.token=admin-token",
        "apex.rate-limit.scanner.limit-per-minute=1",
        "apex.risk.stop-loss-failure-mode=PANIC"
})
@AutoConfigureMockMvc
@Testcontainers
class SystemSafetyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderIntentRepository orderIntentRepository;

    @Autowired
    private ExitRetryRepository exitRetryRepository;

    @Autowired
    private SystemGuardService systemGuardService;

    @Autowired
    private SystemGuardStateRepository systemGuardStateRepository;

    @Autowired
    private TradeStateAuditRepository tradeStateAuditRepository;

    @Autowired
    private TradeCloseService tradeCloseService;


    @Autowired
    private StopLossEnforcementService stopLossEnforcementService;

    @Autowired
    private ReconciliationService reconciliationService;

    @SpyBean
    private EmergencyPanicService emergencyPanicService;

    @SpyBean
    private ExitRetryService exitRetryServiceSpy;

    @MockBean
    private FyersBrokerPort fyersBrokerPort;

    @MockBean
    private PaperBrokerPort paperBrokerPort;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private ExecutionEngine executionEngine;

    @MockBean
    private RiskManagementEngine riskManagementEngine;

    @MockBean
    private CircuitBreakerService circuitBreakerService;

    @MockBean
    private PaperTradingService paperTradingService;

    @MockBean
    private BotScheduler botScheduler;

    @MockBean
    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        exitRetryRepository.deleteAll();
        orderIntentRepository.deleteAll();
        tradeRepository.deleteAll();
        userRepository.deleteAll();
        systemGuardStateRepository.deleteAll();
        tradeStateAuditRepository.deleteAll();

        when(settingsService.isPaperModeForUser(any())).thenReturn(false);
        when(executionEngine.execute(any())).thenReturn(
                new ExecutionEngine.ExecutionResult("client", "broker", ExecutionEngine.ExecutionStatus.PENDING, 0, null, null));
        when(watchlistService.isWatchlistEmpty(any())).thenReturn(false);
    }

    @Test
    void panicEndpointCancelsOrdersFlattensAndIsIdempotent() throws Exception {
        User user = userRepository.save(User.builder()
                .username("trader")
                .passwordHash("hash")
                .email("trader@example.com")
                .build());

        Trade trade = tradeRepository.save(Trade.builder()
                .symbol("AAPL")
                .userId(user.getId())
                .quantity(1)
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(BigDecimal.valueOf(100))
                .entryTime(LocalDateTime.now().minusMinutes(5))
                .isPaperTrade(false)
                .status(Trade.TradeStatus.OPEN)
                .positionState(com.apex.backend.model.PositionState.OPEN)
                .build());

        when(fyersBrokerPort.openOrders(eq(user.getId())))
                .thenReturn(List.of(new com.apex.backend.service.risk.BrokerPort.BrokerOrder("ORD-1", "AAPL", "OPEN", 0, null)));

        String token = jwtTokenProvider.generateToken("trader", user.getId(), "USER");

        mockMvc.perform(post("/api/system/panic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/system/panic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        SystemGuardState state = systemGuardService.getState();
        assertThat(state.isPanicMode()).isTrue();

        verify(fyersBrokerPort, times(1)).cancelOrder(user.getId(), "ORD-1");
        verify(exitRetryServiceSpy, times(1)).enqueueExitAndAttempt(
                Mockito.argThat(candidate -> candidate != null && candidate.getId().equals(trade.getId())),
                eq("EMERGENCY_PANIC"));
    }

    @Test
    void tradeCloseIsIdempotentAndAudited() {
        User user = userRepository.save(User.builder()
                .username("closer")
                .passwordHash("hash")
                .email("close@example.com")
                .build());

        Trade trade = tradeRepository.save(Trade.builder()
                .symbol("MSFT")
                .userId(user.getId())
                .quantity(2)
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(BigDecimal.valueOf(50))
                .entryTime(LocalDateTime.now().minusMinutes(30))
                .isPaperTrade(false)
                .status(Trade.TradeStatus.OPEN)
                .positionState(com.apex.backend.model.PositionState.OPEN)
                .build());

        tradeCloseService.finalizeTrade(trade, BigDecimal.valueOf(55), Trade.ExitReason.MANUAL, "MANUAL_CLOSE");
        tradeCloseService.finalizeTrade(trade, BigDecimal.valueOf(55), Trade.ExitReason.MANUAL, "MANUAL_CLOSE");

        List<TradeStateAudit> audits = tradeStateAuditRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(tradeRepository.findById(trade.getId()).orElseThrow().getStatus()).isEqualTo(Trade.TradeStatus.CLOSED);
    }

    @Test
    void stopLossEnforcementTriggersPanicAndFlatten() {
        User user = userRepository.save(User.builder()
                .username("stopper")
                .passwordHash("hash")
                .email("stop@example.com")
                .build());

        Trade trade = tradeRepository.save(Trade.builder()
                .symbol("TSLA")
                .userId(user.getId())
                .quantity(1)
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(BigDecimal.valueOf(200))
                .entryTime(LocalDateTime.now().minusMinutes(10))
                .isPaperTrade(false)
                .status(Trade.TradeStatus.OPEN)
                .positionState(com.apex.backend.model.PositionState.OPENING)
                .build());

        stopLossEnforcementService.enforce(trade, "STOP_ACK_TIMEOUT");

        verify(executionEngine, times(1)).execute(any());
        assertThat(systemGuardService.getState().isPanicMode()).isTrue();
    }

    @Test
    void exitRetryQueuePersistsAndSchedulesRetry() {
        User user = userRepository.save(User.builder()
                .username("retry")
                .passwordHash("hash")
                .email("retry@example.com")
                .build());

        Trade trade = tradeRepository.save(Trade.builder()
                .symbol("NFLX")
                .userId(user.getId())
                .quantity(1)
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(BigDecimal.valueOf(100))
                .entryTime(LocalDateTime.now().minusMinutes(10))
                .isPaperTrade(false)
                .status(Trade.TradeStatus.OPEN)
                .positionState(com.apex.backend.model.PositionState.OPEN)
                .build());

        exitRetryServiceSpy.enqueueExitAndAttempt(trade, "TEST_RETRY");

        assertThat(exitRetryRepository.findByTradeIdAndResolvedFalse(trade.getId())).hasSize(1);
        assertThat(exitRetryRepository.findByTradeIdAndResolvedFalse(trade.getId()).get(0).getAttempts()).isEqualTo(1);
        assertThat(exitRetryRepository.findByTradeIdAndResolvedFalse(trade.getId()).get(0).getNextAttemptAt()).isNotNull();
    }

    @Test
    void reconciliationMismatchTriggersSafeMode() {
        User user = userRepository.save(User.builder()
                .username("recon")
                .passwordHash("hash")
                .email("recon@example.com")
                .build());

        systemGuardService.setPanicMode(false, null, Instant.now());
        systemGuardService.clearSafeMode();

        orderIntentRepository.save(OrderIntent.builder()
                .clientOrderId("OID-1")
                .userId(user.getId())
                .symbol("AMZN")
                .side("BUY")
                .quantity(1)
                .status("ACKED")
                .orderState(OrderState.ACKED)
                .correlationId("cid")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build());

        when(fyersBrokerPort.openOrders(eq(user.getId()))).thenReturn(List.of());
        when(fyersBrokerPort.openPositions(eq(user.getId()))).thenReturn(List.of());

        reconciliationService.reconcile();

        assertThat(systemGuardService.getState().isSafeMode()).isTrue();
    }

    @Test
    void scanNowRateLimitReturns429() throws Exception {
        User user = userRepository.save(User.builder()
                .username("scanner")
                .passwordHash("hash")
                .email("scan@example.com")
                .build());

        String token = jwtTokenProvider.generateToken("scanner", user.getId(), "USER");

        mockMvc.perform(post("/api/signals/scan-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/signals/scan-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }
}
