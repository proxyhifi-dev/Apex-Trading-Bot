package com.apex.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "tradingExecutor")
    public Executor tradingExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.min(4, processors);
        int maxPoolSize = Math.min(8, processors * 2);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("trading-");
        executor.initialize();
        return executor;
    }
}
