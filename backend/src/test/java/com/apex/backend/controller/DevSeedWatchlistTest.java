package com.apex.backend.controller;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.repository.WatchlistItemRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.dev.enabled=true",
        "APEX_ALLOWED_ORIGINS=https://example.com"
})
@AutoConfigureMockMvc
class DevSeedWatchlistTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    private String token;

    @BeforeEach
    void setup() {
        watchlistItemRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.save(User.builder()
                .username("dev-seed-user")
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
    void seedWatchlistIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/dev/seed-watchlist?count=5&universe=NIFTY100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(5))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.total").value(5));

        mockMvc.perform(post("/api/dev/seed-watchlist?count=5&universe=NIFTY100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skipped").value(5))
                .andExpect(jsonPath("$.total").value(5));

        assertThat(watchlistItemRepository.count()).isEqualTo(5);
    }
}
