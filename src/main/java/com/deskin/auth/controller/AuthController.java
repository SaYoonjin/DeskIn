package com.deskin.auth.controller;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.dto.LoginResponse;
import com.deskin.global.auth.RefreshCookieWriter;
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
        var result = authService.authenticateUser(request);
        cookieWriter.writeCookie(response, result.refreshToken(), result.expiresAt());
        return ApiResponse.success("로그인 되었습니다.", result.response());
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> createUser(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", authService.createUser(request));
    }
}
