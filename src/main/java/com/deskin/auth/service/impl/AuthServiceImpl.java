package com.deskin.auth.service.impl;

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
