package com.apex.backend.security;

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
        "APEX_ALLOWED_ORIGINS=https://example.com"
})
@AutoConfigureMockMvc
class SecurityEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uiConfigIsPublic() throws Exception {
        mockMvc.perform(get("/api/ui/config"))
                .andExpect(status().isOk());
    }

    @Test
    void uiConfigAllowsCorsForConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/api/ui/config")
                        .header("Origin", "https://example.com"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header()
                        .string("Access-Control-Allow-Origin", "https://example.com"));
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/api/strategy/mode"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("JWT missing/expired"));
    }
}
