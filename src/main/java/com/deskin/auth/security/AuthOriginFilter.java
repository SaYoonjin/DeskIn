package com.deskin.auth.security;

import com.deskin.global.config.AuthProperties;
import com.deskin.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@RequiredArgsConstructor
public class AuthOriginFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of(
            "/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout");
    private final AuthProperties authProperties;
    private final SecurityErrorHandler securityErrorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !AUTH_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CORS만으로는 Origin이 없는 요청을 차단하지 못하므로 쿠키 인증 경로에서 별도로 확인한다.
        var origins = Collections.list(request.getHeaders("Origin"));
        if (origins.size() != 1 || "null".equals(origins.get(0))
                || !authProperties.allowedOrigins().contains(origins.get(0))) {
            securityErrorHandler.writeError(response, ErrorCode.INVALID_REQUEST_ORIGIN);
            return;
        }
        chain.doFilter(request, response);
    }
}
