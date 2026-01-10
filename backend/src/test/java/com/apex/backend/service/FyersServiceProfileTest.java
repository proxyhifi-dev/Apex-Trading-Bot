package com.apex.backend.service;

import com.apex.backend.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FyersServiceProfileTest {

    @Test
    void mapsProfileFieldsFromFyersResponse() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        RestTemplate restTemplate = new RestTemplate();
        MetricsService metricsService = new MetricsService(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        BrokerStatusService brokerStatusService = org.mockito.Mockito.mock(BrokerStatusService.class);
        FyersHttpClient fyersHttpClient = new FyersHttpClient(
                restTemplate,
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("fyers"),
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("fyers"),
                io.github.resilience4j.retry.Retry.ofDefaults("fyers"),
                brokerStatusService,
                metricsService
        );
        ReflectionTestUtils.setField(fyersHttpClient, "appId", "APP");
        FyersService service = buildService(environment, fyersHttpClient, metricsService);
        ReflectionTestUtils.setField(service, "accessToken", "token");

        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://api-t1.fyers.in/api/v3/profile"))
                .andExpect(header("Authorization", "APP:token"))
                .andRespond(withSuccess("""
                        {
                          "s": "ok",
                          "message": "Profile retrieved",
                          "data": {
                            "fy_id": "FY123",
                            "name": "Apex Trader",
                            "email_id": "trader@example.com",
                            "mobile_number": "+919999999999"
                          }
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        UserProfile profile = service.getUnifiedUserProfile();

        assertThat(profile.getName()).isEqualTo("Apex Trader");
        assertThat(profile.getClientId()).isEqualTo("FY123");
        assertThat(profile.getBroker()).isEqualTo("FYERS");
        assertThat(profile.getBrokerStatus()).isEqualTo("CONNECTED");
        assertThat(profile.getEmail()).isEqualTo("trader@example.com");
        assertThat(profile.getMobileNumber()).isEqualTo("+919999999999");
        assertThat(profile.getStatusMessage()).isEqualTo("Profile retrieved");

        server.verify();
    }

    @Test
    void returnsDisconnectedProfileWhenTokenMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        RestTemplate restTemplate = new RestTemplate();
        MetricsService metricsService = new MetricsService(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        BrokerStatusService brokerStatusService = org.mockito.Mockito.mock(BrokerStatusService.class);
        FyersHttpClient fyersHttpClient = new FyersHttpClient(
                restTemplate,
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("fyers"),
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("fyers"),
                io.github.resilience4j.retry.Retry.ofDefaults("fyers"),
                brokerStatusService,
                metricsService
        );
        FyersService service = buildService(environment, fyersHttpClient, metricsService);
        ReflectionTestUtils.setField(service, "accessToken", "");

        UserProfile profile = service.getUnifiedUserProfile();

        assertThat(profile.getBrokerStatus()).isEqualTo("DISCONNECTED");
        assertThat(profile.getStatusMessage()).contains("token");
    }

    private FyersService buildService(Environment environment, FyersHttpClient fyersHttpClient, MetricsService metricsService) {
        AlertService alertService = org.mockito.Mockito.mock(AlertService.class);
        FyersTokenService fyersTokenService = org.mockito.Mockito.mock(FyersTokenService.class);
        return new FyersService(environment, metricsService, alertService, fyersHttpClient, fyersTokenService);
    }
}
