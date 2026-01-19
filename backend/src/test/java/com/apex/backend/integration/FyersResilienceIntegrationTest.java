package com.apex.backend.integration;

import com.apex.backend.exception.FyersCircuitOpenException;
import com.apex.backend.service.FyersHttpClient;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class FyersResilienceIntegrationTest {

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
        registry.add("fyers.resilience.retry.max-attempts", () -> "3");
        registry.add("fyers.resilience.retry.base-delay-ms", () -> "10");
        registry.add("fyers.resilience.circuit.failure-rate-threshold", () -> "50");
        registry.add("fyers.resilience.circuit.sliding-window-size", () -> "2");
        registry.add("fyers.resilience.circuit.wait-open-seconds", () -> "60");
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
    private FyersHttpClient fyersHttpClient;

    @Test
    void retriesStopAfterMaxAttempts() {
        stubFor(get(urlEqualTo("/api/v3/profile"))
                .willReturn(serverError()));

        assertThatThrownBy(() -> fyersHttpClient.get("http://localhost:" + wireMock.port() + "/api/v3/profile", "token"))
                .isInstanceOf(Exception.class);

        verify(3, getRequestedFor(urlEqualTo("/api/v3/profile")));
    }

    @Test
    void circuitOpensAfterFailures() {
        stubFor(get(urlEqualTo("/api/v3/positions"))
                .willReturn(serverError()));

        assertThatThrownBy(() -> fyersHttpClient.get("http://localhost:" + wireMock.port() + "/api/v3/positions", "token"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> fyersHttpClient.get("http://localhost:" + wireMock.port() + "/api/v3/positions", "token"))
                .isInstanceOf(Exception.class);

        assertThatThrownBy(() -> fyersHttpClient.get("http://localhost:" + wireMock.port() + "/api/v3/positions", "token"))
                .isInstanceOf(FyersCircuitOpenException.class);

        verify(6, getRequestedFor(urlEqualTo("/api/v3/positions")));
    }
}
