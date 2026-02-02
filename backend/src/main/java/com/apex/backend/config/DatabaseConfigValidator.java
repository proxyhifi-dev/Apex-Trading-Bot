package com.apex.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DatabaseConfigValidator implements ApplicationRunner {

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(dbUrl) || isBlank(dbUser) || isBlank(dbPassword)) {
            String message = "Database configuration missing. Check SPRING_DATASOURCE_URL/USERNAME/PASSWORD.";
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
