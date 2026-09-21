package com.deskin.auth;

import com.deskin.auth.controller.*;
import com.deskin.auth.dto.*;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.service.AuthService;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.auth.security.AuthPrincipal;
import com.deskin.auth.repository.LoginSessionRepository;
import com.deskin.auth.security.RefreshCookieWriter;
import com.deskin.auth.security.SecurityErrorHandler;
import com.deskin.global.config.SecurityConfig;
import com.deskin.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, AuthWebTest.Endpoints.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, RefreshCookieWriter.class,
        GlobalExceptionHandler.class, AuthWebTest.Endpoints.class})
@TestPropertySource(properties = {"auth.session-duration=7d", "auth.cookie-secure=true",
        "auth.allowed-origins=https://app.example.com", "jwt.secret=test-secret-at-least-thirty-two-bytes",
        "jwt.access-token-expire-ms=900000", "jwt.issuer=deskin", "jwt.audience=deskin-web"})
class AuthWebTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean JwtTokenProvider jwtTokens;
    @MockBean LoginSessionRepository sessions;

    @RestController
    static class Endpoints {
        @GetMapping({"/products", "/products/{productId}", "/orders/probe", "/payments/probe", "/seller/probe", "/admin/probe"})
        String getResource() { return "ok"; }

        @PostMapping("/orders/probe")
        String createOrder() { return "ok"; }

        @GetMapping("/orders/principal")
        Long getUserId(@AuthenticationPrincipal AuthPrincipal principal) { return principal.userId(); }
    }

    @Test
    void exposesOnlyProductListAndNumericDetailToAnonymousUsers() throws Exception {
        for (String path : new String[]{"/products", "/products/12"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/products/internal", "/orders/probe", "/seller/probe", "/admin/probe", "/webhooks/toss"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/products")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void grantsOnlyExactRoleWithoutInheritance(UserRole role) throws Exception {
        for (String prefix : new String[]{"/orders", "/payments", "/seller", "/admin"}) {
            UserRole required = switch (prefix) {
                case "/orders", "/payments" -> UserRole.BUYER;
                case "/seller" -> UserRole.SELLER;
                default -> UserRole.ADMIN;
            };
            mockMvc.perform(get(prefix + "/probe").with(user("test").roles(role.name())))
                    .andExpect(status().is(role == required ? 200 : 403));
        }
        mockMvc.perform(get("/unlisted").with(user("test").roles(role.name())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void providesVerifiedPrincipalAndAllowsBearerDomainMutationWithoutCsrf() throws Exception {
        var principal = configureToken();
        mockMvc.perform(get("/orders/principal").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(content().string("12"));
        mockMvc.perform(post("/orders/probe").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verifyNoInteractions(sessions, authService);
    }

    @Test
    void signupReturnsAgreedEnvelopeAndValidatesInputBeforeService() throws Exception {
        when(authService.createUser(any())).thenReturn(new SignupResponse(12L, "buyer_1", UserRole.BUYER));
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"buyer_1","password":"Password123!","name":"이름","role":"BUYER","email":"a@example.com"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.userId").value(12))
                .andExpect(jsonPath("$.data.id").value("buyer_1"));
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verify(authService, times(1)).createUser(any());
    }

    @Test
    void loginKeepsRefreshTokenOutOfJsonAndSetsSecureCookie() throws Exception {
        when(authService.authenticateUser(any())).thenReturn(result());
        var response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"buyer_1\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("refreshToken=refresh-token", "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(response.getContentAsString()).doesNotContain("refresh-token");
    }

    @Test
    void refreshUsesCookieAndLogoutUsesVerifiedPrincipal() throws Exception {
        when(authService.refreshTokens("refresh-token")).thenReturn(result());
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("토큰이 갱신되었습니다."));
        var principal = configureToken();
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(cookie().maxAge("refreshToken", 0));
        verify(authService).revokeSession(principal);
    }

    @Test
    void doesNotExposeCsrfEndpoint() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist("XSRF-TOKEN"));
        verifyNoInteractions(authService);
    }

    @Test
    void corsAllowsOnlyConfiguredOriginAndNeverCreatesHttpSession() throws Exception {
        mockMvc.perform(options("/auth/login").header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        mockMvc.perform(options("/auth/login").header("Origin", "https://other.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private AuthPrincipal configureToken() {
        var principal = new AuthPrincipal(12L, UserRole.BUYER, UUID.randomUUID());
        when(jwtTokens.parseAccessToken("valid")).thenReturn(principal);
        return principal;
    }

    private TokenResult result() {
        return new TokenResult(new LoginResponse("access-token", 12L, UserRole.BUYER),
                "refresh-token", Instant.now().plusSeconds(600));
    }
}
