package com.apex.backend.integration;

import com.apex.backend.model.MarketRegime;
import com.apex.backend.model.MarketRegimeHistory;
import com.apex.backend.repository.MarketRegimeHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MarketRegimeHistoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("apex")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private MarketRegimeHistoryRepository repository;

    @Test
    void persistsRegimeHistory() {
        MarketRegimeHistory history = MarketRegimeHistory.builder()
                .symbol("TEST")
                .timeframe("5m")
                .regime(MarketRegime.TRENDING)
                .adx(28.5)
                .atrPercent(1.2)
                .detectedAt(LocalDateTime.now())
                .build();

        MarketRegimeHistory saved = repository.save(history);
        assertThat(repository.findById(saved.getId())).isPresent();
    }
}
