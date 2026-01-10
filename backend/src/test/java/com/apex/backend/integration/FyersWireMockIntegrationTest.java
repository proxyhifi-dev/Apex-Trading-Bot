package com.apex.backend.integration;

import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.ExecutionEngine;
import com.apex.backend.service.ExecutionEngine.ExecutionResult;
import com.apex.backend.service.ExecutionEngine.ExecutionStatus;
import com.apex.backend.service.ExecutionCostModel;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.marketdata.FyersMarketDataClient;
import com.apex.backend.util.MoneyUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class FyersWireMockIntegrationTest {

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
        registry.add("fyers.api.refresh-url", () -> "http://localhost:" + wireMock.port() + "/api/v3/refresh-token");
        registry.add("fyers.resilience.retry.max-attempts", () -> "3");
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
    private FyersService fyersService;

    @Autowired
    private FyersMarketDataClient marketDataClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private OrderIntentRepository orderIntentRepository;

    @Test
    void tokenExpiredTriggersRefreshAndRetry() throws Exception {
        User user = userRepository.save(User.builder()
                .username("fyers_refresh")
                .passwordHash("hash")
                .email("refresh@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .fyersConnected(true)
                .fyersToken("expired-token")
                .fyersRefreshToken("refresh-token")
                .createdAt(LocalDateTime.now())
                .build());

        stubFor(get(urlEqualTo("/api/v3/profile"))
                .withHeader("Authorization", equalTo("APP:expired-token"))
                .willReturn(unauthorized()));

        stubFor(post(urlEqualTo("/api/v3/refresh-token"))
                .willReturn(okJson("{" +
                        "\"access_token\":\"new-token\"," +
                        "\"refresh_token\":\"refresh-token-2\"" +
                        "}")));

        stubFor(get(urlEqualTo("/api/v3/profile"))
                .withHeader("Authorization", equalTo("APP:new-token"))
                .willReturn(okJson("{" +
                        "\"s\":\"ok\"," +
                        "\"data\":{\"fy_id\":\"FY123\",\"name\":\"Refresh Trader\"}" +
                        "}")));

        Map<String, Object> profile = fyersService.getProfileForUser(user.getId());
        assertThat(profile).containsKey("data");
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getFyersToken()).isEqualTo("new-token");
        assertThat(refreshed.getFyersRefreshToken()).isEqualTo("refresh-token-2");
    }

    @Test
    void rejectedOrderIsCaptured() {
        User user = userRepository.save(User.builder()
                .username("fyers_reject")
                .passwordHash("hash")
                .email("reject@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .fyersConnected(true)
                .fyersToken("live-token")
                .createdAt(LocalDateTime.now())
                .build());

        stubQuote("NSE:ABC-EQ");
        stubFor(post(urlEqualTo("/api/v3/orders"))
                .willReturn(okJson("{\"id\":\"ORD-REJECT\"}")));
        stubFor(get(urlEqualTo("/api/v3/orders?id=ORD-REJECT"))
                .willReturn(okJson("{\"data\":{\"id\":\"ORD-REJECT\",\"status\":\"REJECTED\"}}")));

        ExecutionResult result = executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                user.getId(),
                "NSE:ABC-EQ",
                10,
                ExecutionCostModel.OrderType.MARKET,
                ExecutionCostModel.ExecutionSide.BUY,
                null,
                false,
                "ORD-TEST-REJECT",
                1.5,
                List.of(),
                100.0,
                95.0,
                false
        ));

        assertThat(result.status()).isEqualTo(ExecutionStatus.REJECTED);
    }

    @Test
    void partialFillsUpdateUntilFilled() {
        User user = userRepository.save(User.builder()
                .username("fyers_partial")
                .passwordHash("hash")
                .email("partial@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .fyersConnected(true)
                .fyersToken("live-token-2")
                .createdAt(LocalDateTime.now())
                .build());

        stubQuote("NSE:XYZ-EQ");
        stubFor(post(urlEqualTo("/api/v3/orders"))
                .willReturn(okJson("{\"id\":\"ORD-PARTIAL\"}")));

        stubFor(get(urlEqualTo("/api/v3/orders?id=ORD-PARTIAL"))
                .inScenario("partial-fill")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson("{\"data\":{\"id\":\"ORD-PARTIAL\",\"status\":\"PARTIAL\",\"filledQty\":5,\"avgPrice\":100.0}}"))
                .willSetStateTo("filled"));

        stubFor(get(urlEqualTo("/api/v3/orders?id=ORD-PARTIAL"))
                .inScenario("partial-fill")
                .whenScenarioStateIs("filled")
                .willReturn(okJson("{\"data\":{\"id\":\"ORD-PARTIAL\",\"status\":\"FILLED\",\"filledQty\":10,\"avgPrice\":101.0}}")));

        ExecutionResult result = executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                user.getId(),
                "NSE:XYZ-EQ",
                10,
                ExecutionCostModel.OrderType.MARKET,
                ExecutionCostModel.ExecutionSide.BUY,
                null,
                false,
                "ORD-TEST-PARTIAL",
                1.5,
                List.of(),
                100.0,
                95.0,
                false
        ));

        assertThat(result.status()).isEqualTo(ExecutionStatus.FILLED);
        OrderIntent intent = orderIntentRepository.findByClientOrderId("ORD-TEST-PARTIAL").orElseThrow();
        assertThat(intent.getFilledQuantity()).isEqualTo(10);
        assertThat(intent.getAveragePrice()).isEqualTo(MoneyUtils.bd(101.0));
    }

    @Test
    void rateLimitBackoffRetriesSuccessfully() {
        stubFor(get(urlEqualTo("/data/quotes?symbols=NSE:RATE-EQ"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("success"));

        stubFor(get(urlEqualTo("/data/quotes?symbols=NSE:RATE-EQ"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("success")
                .willReturn(okJson("{\"s\":\"ok\",\"d\":[{\"n\":\"NSE:RATE-EQ\",\"v\":{\"lp\":100.0,\"bp\":99.5,\"ap\":100.5}}]}")));

        assertThat(marketDataClient.getQuote("NSE:RATE-EQ", "live-token")).isPresent();
    }

    private void stubQuote(String symbol) {
        stubFor(get(urlEqualTo("/data/quotes?symbols=" + symbol))
                .willReturn(okJson("{\"s\":\"ok\",\"d\":[{\"n\":\"" + symbol + "\",\"v\":{\"lp\":100.0,\"bp\":99.5,\"ap\":100.5}}]}")));
    }
}
