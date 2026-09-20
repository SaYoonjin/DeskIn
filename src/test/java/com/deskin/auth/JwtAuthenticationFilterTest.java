package com.deskin.auth;

import com.deskin.auth.entity.UserRole;
import com.deskin.global.auth.*;
import com.deskin.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    SessionAuthenticator sessions = mock(SessionAuthenticator.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, sessions, new SecurityErrorHandler(new ObjectMapper()));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void providesPrincipalOnlyAfterValidatingSession() throws Exception {
        var principal = new AuthPrincipal(12L, UserRole.BUYER, UUID.randomUUID());
        when(tokens.parseAccessToken("token")).thenReturn(principal);
        var request = new MockHttpServletRequest("GET", "/orders/1");
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_BUYER");
        });
        verify(sessions).validateSession(principal);
    }

    @Test
    void rejectsInvalidAuthorizationHeaderWithCommonResponse() throws Exception {
        var request = new MockHttpServletRequest("GET", "/orders/1");
        request.addHeader("Authorization", "Basic credentials");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_ACCESS_TOKEN");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsRevokedSessionBeforeDomainController() throws Exception {
        var principal = new AuthPrincipal(12L, UserRole.BUYER, UUID.randomUUID());
        when(tokens.parseAccessToken("token")).thenReturn(principal);
        doThrow(new CustomException(ErrorCode.SESSION_REVOKED)).when(sessions).validateSession(principal);
        var request = new MockHttpServletRequest("GET", "/orders/1");
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_REVOKED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refreshDoesNotDependOnExpiredAccessToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.addHeader("Authorization", "Bearer expired");
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verifyNoInteractions(tokens, sessions);
        assertThat(chain.getRequest()).isNotNull();
    }
}
