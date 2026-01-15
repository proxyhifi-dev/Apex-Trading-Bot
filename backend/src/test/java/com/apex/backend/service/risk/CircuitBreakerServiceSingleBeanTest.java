package com.apex.backend.service.risk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CircuitBreakerServiceSingleBeanTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void onlyCanonicalCircuitBreakerServiceIsActive() {
        assertThat(applicationContext.getBeansOfType(CircuitBreakerService.class)).hasSize(1);
    }
}
