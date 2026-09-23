package com.deskin.auth.service.impl;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.dto.LoginResponse;
import com.deskin.auth.dto.TokenRefreshResponse;
import com.deskin.auth.entity.RefreshToken;
import com.deskin.auth.repository.RefreshTokenRepository;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.auth.token.OpaqueTokenProvider;
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
    public LoginResponse authenticateUser(LoginRequest request) {
        // 사용자 조회 및 비밀번호 검증
        User user = userRepository.findByLoginId(request.id()).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String refreshToken = opaqueTokenProvider.createToken();
        refreshTokenRepository.save(new RefreshToken(opaqueTokenProvider.hashToken(refreshToken),
                user, clock.instant().plus(authProperties.refreshTokenDuration())));
        return new LoginResponse(jwtTokenProvider.createAccessToken(user), refreshToken,
                user.getUserId(), user.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenRefreshResponse refreshAccessToken(String refreshToken) {
        validateRefreshTokenFormat(refreshToken);
        RefreshToken token = refreshTokenRepository.findById(opaqueTokenProvider.hashToken(refreshToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!token.getExpiresAt().isAfter(clock.instant()) || token.getUser() == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new TokenRefreshResponse(jwtTokenProvider.createAccessToken(token.getUser()));
    }

    @Override
    @Transactional
    public SignupResponse createUser(SignupRequest request) {
        // 관리자 계정은 공개 가입으로 생성할 수 없다.
        if (request.role() != UserRole.BUYER && request.role() != UserRole.SELLER) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_ROLE);
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
            sellerRepository.save(new Seller(user));
        }
        userRepository.flush();
        return new SignupResponse(user.getUserId(), user.getName(), user.getRole());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        validateRefreshTokenFormat(refreshToken);
        // 이미 삭제된 토큰으로 재요청해도 성공하여 로그아웃을 반복할 수 있다.
        refreshTokenRepository.deleteByTokenHash(opaqueTokenProvider.hashToken(refreshToken));
    }

    private void validateRefreshTokenFormat(String refreshToken) {
        if (!opaqueTokenProvider.isValidFormat(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
