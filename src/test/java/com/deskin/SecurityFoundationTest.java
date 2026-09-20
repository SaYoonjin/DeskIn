package com.deskin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFoundationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void rejectsUnknownRoutesWithoutCreatingSession() throws Exception {
        mockMvc.perform(get("/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void rejectsAuthMutationWithoutCsrfUsingCommonError() throws Exception {
        mockMvc.perform(post("/auth/signup"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_VALIDATION_FAILED"));
    }

    @Test
    void allowsConfiguredCorsPreflight() throws Exception {
        mockMvc.perform(options("/auth/signup").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/auth/signup").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
