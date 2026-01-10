package com.apex.backend.integration;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.util.MoneyUtils;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DatabaseIntegrationTest {

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
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void migrationsRunAndRepositoriesWork() {
        User user = User.builder()
                .username("integration_user")
                .passwordHash("hash")
                .email("integration@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        User saved = userRepository.save(user);
        assertThat(userRepository.findById(saved.getId())).isPresent();
    }
}
