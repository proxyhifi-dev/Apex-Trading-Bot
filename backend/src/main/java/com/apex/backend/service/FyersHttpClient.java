package com.apex.backend.service;

import com.apex.backend.exception.FyersApiException;
import com.apex.backend.exception.FyersCircuitOpenException;
import com.apex.backend.exception.FyersRateLimitException;
import com.apex.backend.exception.FyersServerException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class FyersHttpClient {

    private final RestTemplate fyersRestTemplate;
    private final CircuitBreaker fyersCircuitBreaker;
    private final RateLimiter fyersRateLimiter;
    private final Retry fyersRetry;
    private final BrokerStatusService brokerStatusService;
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    private final FyersTokenService fyersTokenService;
    private final AuditEventService auditEventService;
    private final LogBroadcastService logBroadcastService;

    @org.springframework.beans.factory.annotation.Value("${fyers.api.app-id:}")
    private String appId;

    @org.springframework.beans.factory.annotation.Value("${fyers.api.rate-limit-backoff-seconds:5}")
    private long rateLimitBackoffSeconds;

    @PostConstruct
    void init() {
        fyersCircuitBreaker.getEventPublisher().onStateTransition(event -> {
            switch (event.getStateTransition().getToState()) {
                case OPEN, HALF_OPEN -> brokerStatusService.markDegraded("FYERS", "CIRCUIT_OPEN");
                case CLOSED -> brokerStatusService.markNormal("FYERS");
                default -> {
                }
            }
        });
        Gauge.builder("broker_circuit_state", fyersCircuitBreaker, breaker -> mapState(breaker.getState()))
                .tag("broker", "FYERS")
                .register(meterRegistry);
    }

    public String get(String url, String token) {
        return execute(url, token, HttpMethod.GET, null, null);
    }

    public String get(String url, String token, Long userId) {
        return execute(url, token, HttpMethod.GET, null, userId);
    }

    public String post(String url, String token, String body) {
        return execute(url, token, HttpMethod.POST, body, null);
    }

    public String post(String url, String token, String body, Long userId) {
        return execute(url, token, HttpMethod.POST, body, userId);
    }

    public String delete(String url, String token) {
        return execute(url, token, HttpMethod.DELETE, null, null);
    }

    public String delete(String url, String token, Long userId) {
        return execute(url, token, HttpMethod.DELETE, null, userId);
    }

    public String put(String url, String token, String body) {
        return execute(url, token, HttpMethod.PUT, body, null);
    }

    public String put(String url, String token, String body, Long userId) {
        return execute(url, token, HttpMethod.PUT, body, userId);
    }

    private String execute(String url, String token, HttpMethod method, String body, Long userId) {
        return executeWithRefresh(url, token, method, body, userId, true);
    }

    private String executeWithRefresh(String url, String token, HttpMethod method, String body, Long userId, boolean allowRefresh) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = false;
        Supplier<String> supplier = () -> doRequest(url, token, method, body);
        try {
            if (brokerStatusService.isRateLimited("FYERS")) {
                throw new FyersRateLimitException("FYERS rate limit backoff active");
            }
            Supplier<String> decorated = Retry.decorateSupplier(fyersRetry, supplier);
            decorated = CircuitBreaker.decorateSupplier(fyersCircuitBreaker, decorated);
            decorated = RateLimiter.decorateSupplier(fyersRateLimiter, decorated);
            String response = decorated.get();
            brokerStatusService.markNormal("FYERS");
            success = true;
            return response;
        } catch (CallNotPermittedException e) {
            brokerStatusService.markDegraded("FYERS", "CIRCUIT_OPEN");
            metricsService.incrementBrokerFailures();
            recordFailure(userId, method, url, null, "CIRCUIT_OPEN", e);
            throw new FyersCircuitOpenException("FYERS circuit breaker open", e);
        } catch (FyersRateLimitException e) {
            brokerStatusService.markRateLimited("FYERS", "RATE_LIMIT",
                    java.time.LocalDateTime.now().plusSeconds(Math.max(1, rateLimitBackoffSeconds)));
            metricsService.incrementBrokerFailures();
            recordFailure(userId, method, url, 429, "RATE_LIMIT", e);
            throw e;
        } catch (FyersApiException e) {
            if (allowRefresh && userId != null && e.getStatusCode() == 401) {
                Optional<String> refreshed = fyersTokenService.refreshAccessToken(userId);
                if (refreshed.isPresent()) {
                    log.info("FYERS token refreshed for user {}, retrying {}", userId, method);
                    return executeWithRefresh(url, refreshed.get(), method, body, userId, false);
                }
            }
            brokerStatusService.markDegraded("FYERS", "HTTP_ERROR");
            metricsService.incrementBrokerFailures();
            recordFailure(userId, method, url, e.getStatusCode(), "HTTP_ERROR", e);
            throw e;
        } catch (Exception e) {
            brokerStatusService.markDegraded("FYERS", "HTTP_ERROR");
            metricsService.incrementBrokerFailures();
            recordFailure(userId, method, url, null, "HTTP_ERROR", e);
            throw e;
        } finally {
            sample.stop(Timer.builder("broker_call_latency")
                    .tag("broker", "FYERS")
                    .tag("method", method.name())
                    .tag("status", success ? "success" : "error")
                    .register(meterRegistry));
        }
    }

    private String doRequest(String url, String token, HttpMethod method, String body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isBlank()) {
                headers.set("Authorization", appId + ":" + token);
            }
            if (method == HttpMethod.POST || method == HttpMethod.PUT) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = fyersRestTemplate.exchange(url, method, entity, String.class);
            return response.getBody();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("FYERS rate limit 429 for {}: {}", url, e.getMessage());
            brokerStatusService.markRateLimited("FYERS", "RATE_LIMIT",
                    java.time.LocalDateTime.now().plusSeconds(Math.max(1, rateLimitBackoffSeconds)));
            throw new FyersRateLimitException("FYERS rate limit", e);
        } catch (HttpServerErrorException e) {
            throw new FyersServerException("FYERS server error (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.warn("FYERS network/server error for {}: {}", url, e.getMessage());
            throw e;
        } catch (HttpClientErrorException e) {
            throw new FyersApiException("FYERS API error (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e.getStatusCode().value(), e);
        }
    }

    private int mapState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0;
            case OPEN -> 1;
            case HALF_OPEN -> 2;
            default -> 3;
        };
    }

    private void recordFailure(Long userId, HttpMethod method, String url, Integer status, String reason, Exception e) {
        log.warn("FYERS request failed method={} url={} status={} userId={} reason={} message={}",
                method, url, status, userId, reason, e.getMessage());
        logBroadcastService.error("FYERS request failed: " + reason);
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("method", method.name());
        metadata.put("url", url);
        if (status != null) {
            metadata.put("status", status);
        }
        metadata.put("reason", reason);
        metadata.put("timestamp", Instant.now().toString());
        auditEventService.recordEvent(userId, "BROKER", "FYERS_HTTP_ERROR",
                "FYERS request failed: " + reason, metadata);
    }
}
