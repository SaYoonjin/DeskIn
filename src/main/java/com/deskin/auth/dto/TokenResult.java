package com.deskin.auth.dto;

import java.time.Instant;

// 갱신 토큰은 컨트롤러에서 쿠키로만 전달하며 이 내부 결과를 JSON으로 반환하지 않는다.
public record TokenResult(LoginResponse response, String refreshToken, Instant expiresAt) {}
