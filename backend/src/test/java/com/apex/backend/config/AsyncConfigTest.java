package com.apex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AsyncConfigTest {

    @Autowired
    @Qualifier("tradingExecutor")
    private Executor tradingExecutor;

    @Test
    void tradingExecutorIsBounded() {
        assertThat(tradingExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) tradingExecutor;
        int processors = Runtime.getRuntime().availableProcessors();
        int expectedCore = Math.min(4, processors);
        int expectedMax = Math.min(8, processors * 2);
        assertThat(executor.getCorePoolSize()).isEqualTo(expectedCore);
        assertThat(executor.getMaxPoolSize()).isEqualTo(expectedMax);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("trading-");
        assertThat(executor.getQueueCapacity()).isEqualTo(200);
    }
}
