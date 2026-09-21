package com.deskin.auth;

import com.deskin.auth.security.AuthOriginFilter;
import com.deskin.auth.security.SecurityErrorHandler;
import com.deskin.global.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class AuthOriginFilterTest {
    private final AuthOriginFilter filter = new AuthOriginFilter(
            new AuthProperties(Duration.ofDays(7), true, List.of("https://app.example.com")),
            new SecurityErrorHandler(new ObjectMapper()));

    @Test
    void rejectsUntrustedNullDuplicateAndMissingOriginsBeforeProcessing() throws Exception {
        for (String[] origins : new String[][]{{}, {"null"}, {"https://evil.example"},
                {"https://app.example.com.evil.example"}, {"https://app.example.com", "https://evil.example"},
                {"https://app.example.com https://evil.example"}, {"http://app.example.com"}}) {
            var request = new MockHttpServletRequest("POST", "/auth/refresh");
            for (String origin : origins) request.addHeader("Origin", origin);
            var response = new MockHttpServletResponse();
            var processed = new AtomicBoolean();
            filter.doFilter(request, response, (incoming, outgoing) -> processed.set(true));
            assertThat(processed).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("INVALID_REQUEST_ORIGIN");
        }
    }

    @Test
    void acceptsConfiguredOriginWithContextPathWithoutCsrfToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/app/auth/refresh");
        request.setContextPath("/app");
        request.addHeader("Origin", "https://app.example.com");
        var processed = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> processed.set(true));
        assertThat(processed).isTrue();
    }

    @Test
    void leavesPreflightAndBearerDomainRequestsToOtherFilters() throws Exception {
        for (var request : List.of(new MockHttpServletRequest("OPTIONS", "/auth/login"),
                new MockHttpServletRequest("POST", "/orders"))) {
            var processed = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> processed.set(true));
            assertThat(processed).isTrue();
        }
    }
}
