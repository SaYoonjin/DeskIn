package com.deskin.auth;

import com.deskin.auth.entity.User;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.repository.*;
import com.deskin.auth.service.impl.AuthServiceImpl;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.auth.token.OpaqueTokenProvider;
import com.deskin.global.config.*;
import com.deskin.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.List;

@ExtendWith(MockitoExtension.class)
abstract class AuthServiceTestSupport {
    @Mock UserRepository users;
    @Mock SellerRepository sellers;
    @Mock LoginSessionRepository sessions;
    @Mock RefreshTokenRepository refreshTokens;
    final Clock clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final OpaqueTokenProvider opaqueTokens = new OpaqueTokenProvider();
    final AuthProperties properties = new AuthProperties(Duration.ofDays(7), true, List.of("https://app.example.com"));
    final JwtProperties jwtProperties = new JwtProperties("unit-test-signing-secret-at-least-32-bytes", 900000, "deskin", "deskin-web");
    final JwtTokenProvider jwtTokens = new JwtTokenProvider(jwtProperties, clock);
    AuthServiceImpl service;

    @BeforeEach
    void initializeService() {
        service = new AuthServiceImpl(users, sellers, encoder, sessions, refreshTokens,
                jwtTokens, opaqueTokens, properties, clock);
        service.initializeDummyPasswordHash();
    }

    User createUser(UserRole role) {
        User user = new User("buyer_1", encoder.encode("Password123!"), "이름", null, "a@example.com", role);
        ReflectionTestUtils.setField(user, "userId", 12L);
        return user;
    }
}
