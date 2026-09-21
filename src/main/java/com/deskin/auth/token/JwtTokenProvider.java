package com.deskin.auth.token;

import com.deskin.auth.security.AuthPrincipal;
import com.deskin.auth.entity.LoginSession;
import com.deskin.auth.entity.UserRole;
import com.deskin.global.config.JwtProperties;
import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // 키가 짧으면 시작 단계에서 실패하여 약한 키로 서비스가 실행되는 것을 막는다.
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(LoginSession session) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(properties.accessTokenExpireMs());
        if (expiresAt.isAfter(session.getExpiresAt())) {
            expiresAt = session.getExpiresAt();
        }
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(session.getUser().getUserId().toString())
                .claim("role", session.getUser().getRole().name()).claim("sessionId", session.getSessionId().toString())
                .issuer(properties.issuer()).audience().add(properties.audience()).and()
                .issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public AuthPrincipal parseAccessToken(String token) {
        try {
            var signed = Jwts.parser().verifyWith(signingKey)
                    .requireIssuer(properties.issuer()).requireAudience(properties.audience())
                    .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(signed.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("허용되지 않은 서명 알고리즘입니다.");
            }
            Claims claims = signed.getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            if (userId <= 0 || claims.getExpiration() == null || claims.getIssuedAt() == null
                    || claims.getIssuedAt().after(Date.from(clock.instant()))) {
                throw new IllegalArgumentException("필수 클레임이 올바르지 않습니다.");
            }
            return new AuthPrincipal(userId, UserRole.valueOf(claims.get("role", String.class)),
                    UUID.fromString(claims.get("sessionId", String.class)));
        } catch (ExpiredJwtException exception) {
            throw new CustomException(ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
    }
}
