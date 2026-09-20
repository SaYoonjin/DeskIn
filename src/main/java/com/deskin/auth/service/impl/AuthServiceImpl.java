package com.deskin.auth.service.impl;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.dto.LoginResponse;
import com.deskin.auth.dto.TokenResult;
import com.deskin.auth.entity.LoginSession;
import com.deskin.auth.entity.RefreshToken;
import com.deskin.auth.repository.LoginSessionRepository;
import com.deskin.auth.repository.RefreshTokenRepository;
import com.deskin.global.auth.JwtTokenProvider;
import com.deskin.global.auth.OpaqueTokenProvider;
import com.deskin.global.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.UUID;
import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.dto.SignupResponse;
import com.deskin.auth.entity.User;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.repository.UserRepository;
import com.deskin.auth.service.AuthService;
import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.global.exception.RefreshTokenReuseException;
import com.deskin.seller.entity.Seller;
import com.deskin.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OpaqueTokenProvider opaqueTokenProvider;
    private final AuthProperties authProperties;
    private final Clock clock;
    private String dummyPasswordHash;

    @PostConstruct
    public void initializeDummyPasswordHash() {
        // 없는 아이디에도 비밀번호 해시 비교를 수행해 계정 존재 여부의 시간 차이를 줄인다.
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public TokenResult authenticateUser(LoginRequest request) {
        // 사용자 조회 및 비밀번호 검증
        User user = userRepository.findByLoginId(request.id()).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 기기별 로그인 세션 생성
        LoginSession session = sessionRepository.save(new LoginSession(user,
                clock.instant().plus(authProperties.sessionDuration())));
        return createTokens(session);
    }

    private TokenResult createTokens(LoginSession session) {
        String refreshToken = opaqueTokenProvider.createToken();
        refreshTokenRepository.save(new RefreshToken(opaqueTokenProvider.hashToken(refreshToken),
                session, clock.instant()));
        var response = new LoginResponse(jwtTokenProvider.createAccessToken(session),
                session.getUser().getUserId(), session.getUser().getRole());
        return new TokenResult(response, refreshToken, session.getExpiresAt());
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public TokenResult refreshTokens(String refreshToken) {
        // 원문 형식을 먼저 검증하고 해시로 로그인 세션 조회
        if (!opaqueTokenProvider.isValidFormat(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String tokenHash = opaqueTokenProvider.hashToken(refreshToken);
        UUID sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 토큰 엔티티를 읽기 전에 잠금을 획득해야 대기 중 변경된 사용 상태를 최신 값으로 읽는다.
        LoginSession session = sessionRepository.findLockedById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken token = refreshTokenRepository.findById(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!session.isActive(clock.instant()) || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 재사용 감지 시 예외 응답이어도 세션 폐기가 커밋되어야 한다.
        if (token.getUsedAt() != null) {
            session.revoke(clock.instant());
            throw new RefreshTokenReuseException();
        }

        // 기존 토큰을 사용 처리하고 최초 세션 만료 시점을 유지한 채 새 토큰 발급
        token.markUsed(clock.instant());
        return createTokens(session);
    }

    @Override
    @Transactional
    public SignupResponse createUser(SignupRequest request) {
        // 가입 역할과 판매자 필수 정보 검증
        if (request.role() != UserRole.BUYER && request.role() != UserRole.SELLER) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_ROLE);
        }
        if ((request.role() == UserRole.SELLER && (request.storeName() == null || request.storeName().isBlank()))
                || (request.role() == UserRole.BUYER && request.storeName() != null)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }

        // 아이디와 이메일 중복 체크
        if (userRepository.existsByLoginId(request.id())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호 해시 및 사용자 생성
        User user = userRepository.save(new User(request.id(), passwordEncoder.encode(request.password()),
                request.name(), request.phone(), request.email(), request.role()));

        // 판매자 생성 실패 시 사용자도 함께 롤백한다.
        if (request.role() == UserRole.SELLER) {
            sellerRepository.save(new Seller(user, request.storeName()));
        }
        userRepository.flush();
        return new SignupResponse(user.getUserId(), user.getLoginId(), user.getRole());
    }
}
