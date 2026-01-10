package com.apex.backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import com.apex.backend.exception.FyersRateLimitException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

@Configuration
public class FyersResilienceConfig {

    @Bean
    public CircuitBreaker fyersCircuitBreaker(
            @Value("${fyers.resilience.circuit.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${fyers.resilience.circuit.wait-open-seconds:30}") long waitOpenSeconds,
            @Value("${fyers.resilience.circuit.sliding-window-size:20}") int slidingWindowSize
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitOpenSeconds))
                .slidingWindowSize(slidingWindowSize)
                .build();
        return CircuitBreaker.of("fyers", config);
    }

    @Bean
    public RateLimiter fyersRateLimiter(
            @Value("${fyers.resilience.rate.limit-per-second:8}") int limitPerSecond,
            @Value("${fyers.resilience.rate.timeout-ms:0}") long timeoutMs
    ) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(limitPerSecond)
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .build();
        return RateLimiter.of("fyers", config);
    }

    @Bean
    public Retry fyersRetry(
            @Value("${fyers.resilience.retry.max-attempts:4}") int maxAttempts,
            @Value("${fyers.resilience.retry.base-delay-ms:500}") long baseDelayMs,
            @Value("${fyers.resilience.retry.jitter-factor:0.2}") double jitterFactor
    ) {
        IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(
                Duration.ofMillis(baseDelayMs),
                2.0,
                jitterFactor
        );
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(intervalFunction)
                .retryExceptions(FyersRateLimitException.class, ResourceAccessException.class, HttpServerErrorException.class)
                .build();
        return Retry.of("fyers", config);
    }
}
