package com.apex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901"
})
class CorsConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AllowedOriginResolver allowedOriginResolver;

    @Test
    void corsConfigurationSourceIsSingleBean() {
        assertThat(applicationContext.getBeansOfType(CorsConfigurationSource.class)).hasSize(1);
    }

    @Test
    void devOriginsIncludeLocalhostDefaults() {
        List<String> origins = allowedOriginResolver.resolveCorsAllowedOrigins();
        assertThat(origins).contains("http://localhost:4200");
    }
}
