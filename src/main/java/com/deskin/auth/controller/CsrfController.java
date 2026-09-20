package com.deskin.auth.controller;

import com.deskin.global.dto.ApiResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/auth/csrf")
    public ApiResponse<CsrfResponse> getCsrfToken(CsrfToken token) {
        // HttpOnly 쿠키를 직접 읽을 수 없는 프론트에 요청 헤더용 토큰을 전달한다.
        return ApiResponse.success("요청 검증 토큰이 발급되었습니다.",
                new CsrfResponse(token.getToken(), token.getHeaderName()));
    }

    public record CsrfResponse(String csrfToken, String headerName) {}
}
