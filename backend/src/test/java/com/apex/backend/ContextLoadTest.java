package com.apex.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContextLoadTest {

    @Test
    void applicationContextLoads() {
        assertThat(true).isTrue();
    }
}
