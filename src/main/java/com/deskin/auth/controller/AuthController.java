package com.deskin.auth.controller;

import com.deskin.auth.dto.*;
import com.deskin.auth.service.AuthService;
import com.deskin.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> authenticateUser(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인 되었습니다.", authService.authenticateUser(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refreshAccessToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("토큰이 갱신되었습니다.", authService.refreshAccessToken(request.refreshToken()));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> createUser(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", authService.createUser(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success("로그아웃 되었습니다.");
    }
}
