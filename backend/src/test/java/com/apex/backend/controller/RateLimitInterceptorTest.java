package com.apex.backend.controller;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.rate-limit.scanner.limit-per-minute=1",
        "apex.rate-limit.scanner.timeout-ms=0",
        "APEX_ALLOWED_ORIGINS=https://example.com"
})
@AutoConfigureMockMvc
class RateLimitInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        User user = userRepository.save(User.builder()
                .username("rate-limit-user")
                .passwordHash("pass")
                .role("USER")
                .availableFunds(MoneyUtils.bd(100000))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());
        token = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
    }

    @Test
    void scannerRateLimitReturnsRetryAfterHeader() throws Exception {
        mockMvc.perform(post("/api/signals/scan-now")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/signals/scan-now")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }
}
