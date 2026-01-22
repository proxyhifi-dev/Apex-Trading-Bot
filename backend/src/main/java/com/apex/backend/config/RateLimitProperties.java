package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "apex.rate-limit")
@Data
public class RateLimitProperties {

    private Scanner scanner = new Scanner();
    private Trade trade = new Trade();

    @Data
    public static class Scanner {
        private int limitPerMinute = 2;
        private long timeoutMs = 0;
    }

    @Data
    public static class Trade {
        private int limitPerSecond = 5;
        private long timeoutMs = 0;
    }
}
