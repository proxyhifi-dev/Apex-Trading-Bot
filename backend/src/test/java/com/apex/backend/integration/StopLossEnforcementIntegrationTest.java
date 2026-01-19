package com.apex.backend.integration;

import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.AuditEventRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.StopLossEnforcementService;
import com.apex.backend.util.MoneyUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class StopLossEnforcementIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(0);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("apex_trading_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("fyers.api.app-id", () -> "APP");
        registry.add("fyers.api.base-url", () -> "http://localhost:" + wireMock.port() + "/api/v3");
        registry.add("fyers.data.base-url", () -> "http://localhost:" + wireMock.port() + "/data");
        registry.add("fyers.api.access-token", () -> "test-token");
        registry.add("execution.stop-ack-timeout-seconds", () -> "2");
        registry.add("execution.poll.max-attempts", () -> "1");
        registry.add("execution.poll.delay-ms", () -> "10");
    }

    static {
        wireMock.start();
        configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private StopLossEnforcementService stopLossEnforcementService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void stopLossTimeoutTriggersFlattenAndAudit() {
        User user = userRepository.save(User.builder()
                .username("stoploss_user")
                .passwordHash("hash")
                .email("stoploss@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .fyersToken("live-token")
                .fyersConnected(true)
                .createdAt(LocalDateTime.now())
                .build());

        Trade trade = tradeRepository.save(Trade.builder()
                .symbol("NSE:INFY-EQ")
                .userId(user.getId())
                .tradeType(Trade.TradeType.LONG)
                .quantity(1)
                .entryPrice(MoneyUtils.bd(100.0))
                .entryTime(LocalDateTime.now().minusSeconds(10))
                .stopLoss(MoneyUtils.bd(95.0))
                .currentStopLoss(MoneyUtils.bd(95.0))
                .atr(MoneyUtils.bd(1.0))
                .highestPrice(MoneyUtils.bd(100.0))
                .isPaperTrade(false)
                .status(Trade.TradeStatus.OPEN)
                .positionState(PositionState.OPENING)
                .build());

        stubFor(get(urlEqualTo("/data/quotes?symbols=NSE:INFY-EQ"))
                .willReturn(okJson("{\"s\":\"ok\",\"d\":[{\"n\":\"NSE:INFY-EQ\",\"v\":{\"lp\":100.0,\"bp\":99.5,\"ap\":100.5}}]}")));
        stubFor(post(urlEqualTo("/api/v3/orders"))
                .willReturn(okJson("{\"id\":\"ORD-FLATTEN\"}")));
        stubFor(get(urlEqualTo("/api/v3/orders?id=ORD-FLATTEN"))
                .willReturn(okJson("{\"data\":{\"id\":\"ORD-FLATTEN\",\"status\":\"FILLED\",\"filledQty\":1,\"avgPrice\":100.0}}")));

        stopLossEnforcementService.enforceOverdueStops();

        Trade updated = tradeRepository.findById(trade.getId()).orElseThrow();
        assertThat(updated.getPositionState()).isEqualTo(PositionState.ERROR);
        assertThat(auditEventRepository.findAll()).anyMatch(event -> "STOP_LOSS".equals(event.getEventType()));
        verify(postRequestedFor(urlEqualTo("/api/v3/orders")));
    }
}
