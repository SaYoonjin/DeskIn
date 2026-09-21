package com.deskin.auth.controller;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.dto.LoginResponse;
import com.deskin.auth.dto.TokenResult;
import com.deskin.auth.security.RefreshCookieWriter;
import com.deskin.auth.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.dto.SignupResponse;
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
    private final RefreshCookieWriter cookieWriter;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> authenticateUser(@Valid @RequestBody LoginRequest request,
                                                       HttpServletResponse response) {
        return createTokenResponse("로그인 되었습니다.", authService.authenticateUser(request), response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshTokens(
            @CookieValue(name = RefreshCookieWriter.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        return createTokenResponse("토큰이 갱신되었습니다.", authService.refreshTokens(refreshToken), response);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> createUser(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", authService.createUser(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> revokeSession(@AuthenticationPrincipal AuthPrincipal principal,
                                           HttpServletResponse response) {
        authService.revokeSession(principal);
        cookieWriter.clearCookie(response);
        return ApiResponse.success("로그아웃 되었습니다.");
    }

    private ApiResponse<LoginResponse> createTokenResponse(String message, TokenResult result,
                                                           HttpServletResponse response) {
        // 쿠키는 HTTP 응답 처리이므로 서비스의 토큰 검증·발급과 분리한다.
        cookieWriter.writeCookie(response, result.refreshToken(), result.expiresAt());
        return ApiResponse.success(message, result.response());
    }
}
