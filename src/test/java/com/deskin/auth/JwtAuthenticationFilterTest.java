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
    void authEndpointsDoNotDependOnAccessTokenIncludingWithContextPath() throws Exception {
        for (String contextPath : new String[]{"", "/app"}) {
            for (String path : new String[]{"/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout"}) {
                var request = new MockHttpServletRequest("POST", contextPath + path);
                request.setContextPath(contextPath);
                request.addHeader("Authorization", "Bearer expired");
                var chain = new MockFilterChain();
                filter.doFilter(request, new MockHttpServletResponse(), chain);
                assertThat(chain.getRequest()).isNotNull();
            }
        }
        verifyNoInteractions(tokens);
    }

    @Test
    void missingAuthorizationContinuesWithoutReturningUnauthorized() throws Exception {
        var request = new MockHttpServletRequest("GET", "/orders/1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verifyNoInteractions(tokens);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void sameAuthPathWithGetMethodStillRequiresAccessToken() throws Exception {
        var request = new MockHttpServletRequest("GET", "/auth/login");
        request.addHeader("Authorization", "Basic credentials");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_ACCESS_TOKEN");
    }
}
