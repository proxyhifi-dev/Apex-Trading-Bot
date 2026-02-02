package com.apex.backend.service;

import com.apex.backend.model.AuditEvent;
import com.apex.backend.model.User;
import com.apex.backend.repository.AuditEventRepository;
import com.apex.backend.repository.UserRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FyersServiceIntegrationTest {

    private static final MockWebServer mockWebServer;

    static {
        try {
            mockWebServer = new MockWebServer();
            mockWebServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String apiBase = mockWebServer.url("/api/v3").toString().replaceAll("/$", "");
        String dataBase = mockWebServer.url("/data").toString().replaceAll("/$", "");
        registry.add("fyers.api.base-url", () -> apiBase);
        registry.add("fyers.data.base-url", () -> dataBase);
        registry.add("fyers.api.app-id", () -> "app-id-123");
        registry.add("fyers.api.http.connect-timeout-ms", () -> 200);
        registry.add("fyers.api.http.read-timeout-ms", () -> 200);
        registry.add("fyers.resilience.retry.max-attempts", () -> 2);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    @Autowired
    private FyersService fyersService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockBean
    private FyersAuthService fyersAuthService;

    @MockBean
    private LogBroadcastService logBroadcastService;

    @BeforeEach
    void resetState() {
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
        try {
            while (mockWebServer.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
                // drain queued requests
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    void shutdown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void placeOrderSendsExpectedPayload() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"order-123\"}"));

        String orderId = fyersService.placeOrder("NSE:SBIN-EQ", 5, "BUY", "MARKET", 0.0, "client-1", user.getId());

        assertThat(orderId).isEqualTo("order-123");
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/v3/orders");
        assertThat(request.getHeader("Authorization")).isEqualTo("app-id-123:token-1");
        assertThat(request.getBody().readUtf8())
                .contains("\"symbol\":\"NSE:SBIN-EQ\"")
                .contains("\"qty\":5")
                .contains("\"side\":1")
                .contains("\"type\":2")
                .contains("\"clientId\":\"client-1\"");
    }

    @Test
    void modifyOrderSendsExpectedPayload() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"order-456\"}"));

        var request = new com.apex.backend.dto.OrderModifyRequest();
        request.setQty(10);
        request.setPrice(210.5);
        String orderId = fyersService.modifyOrder("order-456", request, user.getId());

        assertThat(orderId).isEqualTo("order-456");
        RecordedRequest recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/v3/orders");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("app-id-123:token-1");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"id\":\"order-456\"")
                .contains("\"qty\":10")
                .contains("\"limitPrice\":210.5");
    }

    @Test
    void cancelOrderUsesDelete() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"order-789\"}"));

        String responseId = fyersService.cancelOrder("order-789", null, user.getId());

        assertThat(responseId).isEqualTo("order-789");
        RecordedRequest recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/v3/orders/order-789");
    }

    @Test
    void getPositionsHitsEndpoint() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));

        Map<String, Object> positions = fyersService.getPositionsForUser(user.getId());

        assertThat(positions).containsKey("data");
        RecordedRequest recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/api/v3/positions");
    }

    @Test
    void getQuotesUsesDataEndpoint() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"s\":\"ok\",\"d\":[{\"n\":\"NSE:SBIN-EQ\",\"v\":{\"lp\":123.4}}]}"));

        Map<String, java.math.BigDecimal> quotes = fyersService.getLtpBatch(List.of("NSE:SBIN-EQ"), "token-1", user.getId());

        assertThat(quotes).containsKey("NSE:SBIN-EQ");
        RecordedRequest recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/data/quotes?symbols=NSE:SBIN-EQ");
    }

    @Test
    void refreshesTokenOnUnauthorizedOnce() throws Exception {
        User user = createUser("token-1", "refresh-1");
        when(fyersAuthService.refreshAccessToken("refresh-1"))
                .thenReturn(new FyersAuthService.FyersTokens("token-2", "refresh-2"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));

        Map<String, Object> positions = fyersService.getPositionsForUser(user.getId());

        assertThat(positions).containsKey("data");
        RecordedRequest first = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest second = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(first.getHeader("Authorization")).isEqualTo("app-id-123:token-1");
        assertThat(second.getHeader("Authorization")).isEqualTo("app-id-123:token-2");
    }

    @Test
    void retriesOnRateLimit() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("{\"message\":\"Rate limit\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));

        Map<String, Object> positions = fyersService.getPositionsForUser(user.getId());

        assertThat(positions).containsKey("data");
        assertThat(mockWebServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(mockWebServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void retriesOnServerErrors() throws Exception {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"Oops\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));

        Map<String, Object> positions = fyersService.getPositionsForUser(user.getId());

        assertThat(positions).containsKey("data");
        assertThat(mockWebServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(mockWebServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void timeoutCreatesAuditEvent() {
        User user = createUser("token-1", "refresh-1");
        mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> fyersService.getPositionsForUser(user.getId()))
                .isInstanceOf(Exception.class);

        List<AuditEvent> events = auditEventRepository.findAll();
        assertThat(events).isNotEmpty();
    }

    private User createUser(String token, String refreshToken) {
        User user = User.builder()
                .username("user-" + token)
                .passwordHash("hash")
                .email("user-" + token + "@example.com")
                .fyersConnected(true)
                .fyersTokenActive(true)
                .fyersToken(token)
                .fyersRefreshToken(refreshToken)
                .build();
        return userRepository.save(user);
    }
}
