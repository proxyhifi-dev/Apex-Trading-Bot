package com.apex.backend.service;

import com.apex.backend.exception.FyersApiException;
import com.apex.backend.exception.FyersRateLimitException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @org.springframework.beans.factory.annotation.Value("${fyers.api.app-id:}")
    private String appId;

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
    }

    public String get(String url, String token) {
        return execute(url, token, HttpMethod.GET, null);
    }

    public String post(String url, String token, String body) {
        return execute(url, token, HttpMethod.POST, body);
    }

    public String delete(String url, String token) {
        return execute(url, token, HttpMethod.DELETE, null);
    }

    public String put(String url, String token, String body) {
        return execute(url, token, HttpMethod.PUT, body);
    }

    private String execute(String url, String token, HttpMethod method, String body) {
        Supplier<String> supplier = () -> doRequest(url, token, method, body);
        try {
            Supplier<String> decorated = Retry.decorateSupplier(fyersRetry, supplier);
            decorated = CircuitBreaker.decorateSupplier(fyersCircuitBreaker, decorated);
            decorated = RateLimiter.decorateSupplier(fyersRateLimiter, decorated);
            String response = decorated.get();
            brokerStatusService.markNormal("FYERS");
            return response;
        } catch (FyersRateLimitException e) {
            brokerStatusService.markDegraded("FYERS", "RATE_LIMIT");
            metricsService.incrementBrokerFailures();
            throw e;
        } catch (Exception e) {
            brokerStatusService.markDegraded("FYERS", "HTTP_ERROR");
            metricsService.incrementBrokerFailures();
            throw e;
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
            throw new FyersRateLimitException("FYERS rate limit", e);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            log.warn("FYERS network/server error for {}: {}", url, e.getMessage());
            throw e;
        } catch (HttpClientErrorException e) {
            throw new FyersApiException("FYERS API error (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e.getStatusCode().value(), e);
        }
    }
}
