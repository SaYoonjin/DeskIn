package com.deskin.auth.security;

import com.deskin.global.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefreshCookieWriter {
    public static final String COOKIE_NAME = "refreshToken";
    private final AuthProperties properties;
    private final Clock clock;

    public void writeCookie(HttpServletResponse response, String token, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(token,
                remaining.isNegative() ? Duration.ZERO : remaining).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie("", Duration.ZERO).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private ResponseCookie createCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(properties.cookieSecure())
                .sameSite("Lax").path("/auth").maxAge(maxAge).build();
    }
}
