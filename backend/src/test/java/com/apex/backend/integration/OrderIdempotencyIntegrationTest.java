package com.apex.backend.integration;

import com.apex.backend.dto.PlaceOrderRequest;
import com.apex.backend.model.Settings;
import com.apex.backend.model.User;
import com.apex.backend.repository.SettingsRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.OrderExecutionService;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class OrderIdempotencyIntegrationTest {

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
        registry.add("fyers.api.access-token", () -> "test-token");
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
    private OrderExecutionService orderExecutionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Test
    void replayUsesStoredResponseAndDoesNotReorder() {
        User user = userRepository.save(User.builder()
                .username("idem_user")
                .passwordHash("hash")
                .email("idem@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .fyersConnected(true)
                .createdAt(LocalDateTime.now())
                .build());

        settingsRepository.save(Settings.builder()
                .userId(user.getId())
                .mode("LIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        stubFor(post(urlEqualTo("/api/v3/orders"))
                .willReturn(okJson("{\"id\":\"ORD-IDEMPOTENT\"}")));

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .exchange("NSE")
                .symbol("NSE:INFY-EQ")
                .side(PlaceOrderRequest.OrderSide.BUY)
                .qty(1)
                .orderType(PlaceOrderRequest.OrderType.MARKET)
                .productType(PlaceOrderRequest.ProductType.INTRADAY)
                .validity(PlaceOrderRequest.Validity.DAY)
                .clientOrderId("IDEMPOTENT-ORDER-1")
                .build();

        var first = orderExecutionService.placeOrder(user.getId(), request);
        var second = orderExecutionService.placeOrder(user.getId(), request);

        assertThat(first.getBrokerOrderId()).isEqualTo("ORD-IDEMPOTENT");
        assertThat(second.getBrokerOrderId()).isEqualTo("ORD-IDEMPOTENT");
        verify(1, postRequestedFor(urlEqualTo("/api/v3/orders")));
    }
}
