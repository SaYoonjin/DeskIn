package com.deskin.auth;

import com.deskin.auth.entity.UserRole;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.auth.security.AuthPrincipal;
import com.deskin.auth.security.JwtAuthenticationFilter;
import com.deskin.auth.security.SecurityErrorHandler;
import com.deskin.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, new SecurityErrorHandler(new ObjectMapper()));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void providesPrincipalFromJwtOnly() throws Exception {
        var principal = new AuthPrincipal(12L, UserRole.BUYER);
        when(tokens.parseAccessToken("token")).thenReturn(principal);
        var request = new MockHttpServletRequest("GET", "/orders/1");
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_BUYER");
        });
        verify(tokens).parseAccessToken("token");
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
    void refreshDoesNotDependOnExpiredAccessToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.addHeader("Authorization", "Bearer expired");
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verifyNoInteractions(tokens);
        assertThat(chain.getRequest()).isNotNull();
    }
}
