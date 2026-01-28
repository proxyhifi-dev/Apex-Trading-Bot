package com.apex.backend.config;

import com.apex.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigProdTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BackendApplication.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "jwt.secret=01234567890123456789012345678901"
            );

    @Test
    void prodOriginsIncludeEnv() {
        contextRunner
                .withPropertyValues("APEX_ALLOWED_ORIGINS=https://example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AllowedOriginResolver resolver = context.getBean(AllowedOriginResolver.class);
                    assertThat(resolver.resolveCorsAllowedOrigins()).contains("https://example.com");
                });
    }

    @Test
    void prodOriginsCannotBeEmpty() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }
}
