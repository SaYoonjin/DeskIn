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
        var session = new LoginSession(createUser(UserRole.SELLER), clock.instant().plusSeconds(604800));
        var principal = jwtTokens.parseAccessToken(jwtTokens.createAccessToken(session));
        assertThat(principal.userId()).isEqualTo(12L);
        assertThat(principal.role()).isEqualTo(UserRole.SELLER);
        assertThat(principal.sessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    void issuesDifferentAccessTokensWithinSameSecond() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(300));
        assertThat(jwtTokens.createAccessToken(session)).isNotEqualTo(jwtTokens.createAccessToken(session));
    }

    @Test
    void rejectsExpiredToken() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(604800));
        String token = jwtTokens.createAccessToken(session);
        var later = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofMinutes(16)));
        assertError(() -> later.parseAccessToken(token), ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void keepsFifteenMinuteAccessLifetimeIndependentOfSession() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(30));
        String token = jwtTokens.createAccessToken(session);
        var later = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofSeconds(31)));
        session.revoke(clock.instant());
        assertThat(later.parseAccessToken(token).userId()).isEqualTo(12L);
        var expired = new JwtTokenProvider(jwtProperties, Clock.offset(clock, Duration.ofMinutes(15).plusSeconds(1)));
        assertError(() -> expired.parseAccessToken(token), ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void rejectsDifferentSignatureIssuerAndAudience() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(604800));
        for (var properties : new JwtProperties[]{
                new JwtProperties("different-signing-secret-at-least-32-bytes", 900000, "deskin", "deskin-web"),
                new JwtProperties(jwtProperties.secret(), 900000, "wrong", "deskin-web"),
                new JwtProperties(jwtProperties.secret(), 900000, "deskin", "wrong")}) {
            String token = new JwtTokenProvider(properties, clock).createAccessToken(session);
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
