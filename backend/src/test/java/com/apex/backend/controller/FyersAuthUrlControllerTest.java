package com.apex.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "fyers.api.app-id=",
        "fyers.redirect-uri=",
        "APEX_ALLOWED_ORIGINS=https://example.com"
})
@AutoConfigureMockMvc
class FyersAuthUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingFyersConfigReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/fyers/auth-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("FYERS app-id is required"));
    }
}
