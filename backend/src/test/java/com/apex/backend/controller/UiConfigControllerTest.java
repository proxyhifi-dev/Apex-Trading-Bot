package com.apex.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.dev.endpoints=true",
        "apex.ui.api-base-url=http://localhost:8080/api",
        "apex.ui.ws-url=ws://localhost:8080/ws",
        "APEX_ALLOWED_ORIGINS=https://example.com"
})
@AutoConfigureMockMvc
class UiConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uiConfigIncludesDevFlagsAndServerTime() throws Exception {
        mockMvc.perform(get("/api/ui/config"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.apiBaseUrl").value("http://localhost:8080/api"))
                .andExpect(jsonPath("$.wsBaseUrl").value("ws://localhost:8080/ws"))
                .andExpect(jsonPath("$.devMode").value(true))
                .andExpect(jsonPath("$.serverTime").exists());
    }
}
