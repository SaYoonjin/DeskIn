package com.deskin.auth;

import com.deskin.auth.security.AuthRateLimitFilter;
import com.deskin.auth.security.SecurityErrorHandler;
import com.deskin.global.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new AuthProperties(Duration.ofDays(7), List.of("http://localhost:3000"), 2, Duration.ofMinutes(1)),
            new SecurityErrorHandler(new ObjectMapper()), clock);

    @Test
    void limitsLoginAttemptsPerIpAndPath() throws Exception {
        var processed = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            filter.doFilter(request("POST", "/auth/login", "192.0.2.10"),
                    new MockHttpServletResponse(), (request, response) -> processed.incrementAndGet());
        }
        var blocked = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/auth/login", "192.0.2.10"), blocked,
                (request, response) -> processed.incrementAndGet());

        assertThat(processed).hasValue(2);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void keepsSignupAndLoginCountersSeparateAndLeavesOtherMethodsUntouched() throws Exception {
        var processed = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            filter.doFilter(request("POST", "/auth/signup", "192.0.2.20"),
                    new MockHttpServletResponse(), (request, response) -> processed.incrementAndGet());
        }
        var loginResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/auth/login", "192.0.2.20"), loginResponse,
                (request, response) -> processed.incrementAndGet());
        var getResponse = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/auth/login", "192.0.2.20"), getResponse,
                (request, response) -> processed.incrementAndGet());

        assertThat(processed).hasValue(4);
        assertThat(loginResponse.getStatus()).isEqualTo(200);
        assertThat(getResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void resetsCounterAfterWindow() throws Exception {
        var mutableClock = new TestClock(Instant.parse("2026-09-22T00:00:00Z"));
        var first = new AuthRateLimitFilter(
                new AuthProperties(Duration.ofDays(7), List.of("http://localhost:3000"), 1, Duration.ofMinutes(1)),
                new SecurityErrorHandler(new ObjectMapper()), mutableClock);
        first.doFilter(request("POST", "/auth/login", "192.0.2.30"), new MockHttpServletResponse(),
                (request, response) -> {});
        var blocked = new MockHttpServletResponse();
        first.doFilter(request("POST", "/auth/login", "192.0.2.30"), blocked,
                (request, response) -> {});
        assertThat(blocked.getStatus()).isEqualTo(429);
        mutableClock.advance(Duration.ofMinutes(1));
        var allowed = new MockHttpServletResponse();
        first.doFilter(request("POST", "/auth/login", "192.0.2.30"), allowed,
                (request, response) -> {});
        assertThat(allowed.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddress) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static final class TestClock extends Clock {
        private Instant current;

        private TestClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
