package com.deskin.auth.security;

import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> AUTHENTICATION_EXCLUDED_PATHS = Set.of(
            "/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout");

    private final JwtTokenProvider tokens;
    private final SecurityErrorHandler errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // 갱신과 로그아웃은 Access Token 만료 여부와 관계없이 본문의 Refresh Token으로 처리한다.
        return HttpMethod.POST.matches(request.getMethod())
                && AUTHENTICATION_EXCLUDED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
                    throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
                }
                AuthPrincipal principal = tokens.parseAccessToken(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (CustomException exception) {
                SecurityContextHolder.clearContext();
                errors.writeError(response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
