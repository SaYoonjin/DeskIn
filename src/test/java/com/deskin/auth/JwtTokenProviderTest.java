package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.global.config.JwtProperties;
import com.deskin.global.exception.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest extends AuthServiceTestSupport {
    @Test
    void roundTripsRequiredClaims() {
        var user = createUser(UserRole.SELLER);
        var principal = jwtTokens.parseAccessToken(jwtTokens.createAccessToken(user));
        assertThat(principal.userId()).isEqualTo(12L);
        assertThat(principal.role()).isEqualTo(UserRole.SELLER);
    }

    @Test
    void issuesDifferentAccessTokensWithinSameSecond() {
        var user = createUser(UserRole.BUYER);
        assertThat(jwtTokens.createAccessToken(user)).isNotEqualTo(jwtTokens.createAccessToken(user));
    }

    @Test
    void rejectsExpiredToken() {
        var user = createUser(UserRole.BUYER);
        String token = jwtTokens.createAccessToken(user);
        var later = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofMinutes(16)));
        assertError(() -> later.parseAccessToken(token), ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void keepsFifteenMinuteAccessLifetime() {
        var user = createUser(UserRole.BUYER);
        String token = jwtTokens.createAccessToken(user);
        var later = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofSeconds(31)));
        assertThat(later.parseAccessToken(token).userId()).isEqualTo(12L);
        var expired = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofMinutes(15).plusSeconds(1)));
        assertError(() -> expired.parseAccessToken(token), ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void rejectsDifferentSignatureIssuerAndAudience() {
        var user = createUser(UserRole.BUYER);
        for (var properties : new JwtProperties[]{
                new JwtProperties("different-signing-secret-at-least-32-bytes", 900000, "deskin", "deskin-web"),
                new JwtProperties(jwtProperties.secret(), 900000, "wrong", "deskin-web"),
                new JwtProperties(jwtProperties.secret(), 900000, "deskin", "wrong")}) {
            String token = new JwtTokenProvider(properties, clock).createAccessToken(user);
            assertError(() -> jwtTokens.parseAccessToken(token), ErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    @Test
    void rejectsMissingClaimsAndMalformedTokens() {
        String token = Jwts.builder().subject("12").issuer("deskin").audience().add("deskin-web").and()
                .expiration(Date.from(clock.instant().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8))).compact();
        assertError(() -> jwtTokens.parseAccessToken(token), ErrorCode.INVALID_ACCESS_TOKEN);
        assertError(() -> jwtTokens.parseAccessToken("invalid"), ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void rejectsWeakSigningKeyAtStartup() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("short", 900000, "deskin", "deskin-web"), clock))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
