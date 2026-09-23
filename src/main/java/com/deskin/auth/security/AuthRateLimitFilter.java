package com.deskin.auth.security;

import com.deskin.global.config.AuthProperties;
import com.deskin.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> RATE_LIMITED_PATHS = Set.of("/auth/signup", "/auth/login");

    private final AuthProperties properties;
    private final SecurityErrorHandler errors;
    private final Clock clock;
    private final Map<String, RateLimitState> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !HttpMethod.POST.matches(request.getMethod()) || !RATE_LIMITED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        Instant now = clock.instant();
        RateLimitState state = attempts.compute(key, (ignored, previous) -> nextState(previous, now));
        if (state.count() > properties.rateLimitMaxAttempts()) {
            response.setHeader("Retry-After", Long.toString(properties.rateLimitWindow().toSeconds()));
            errors.writeError(response, ErrorCode.RATE_LIMIT_EXCEEDED);
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimitState nextState(RateLimitState previous, Instant now) {
        if (previous == null || !now.isBefore(previous.resetAt())) {
            return new RateLimitState(1, now.plus(properties.rateLimitWindow()));
        }
        return new RateLimitState(previous.count() + 1, previous.resetAt());
    }

    private record RateLimitState(int count, Instant resetAt) {}
}
