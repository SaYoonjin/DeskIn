package com.deskin.global.auth;

import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final SessionAuthenticator sessions;
    private final SecurityErrorHandler errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // 갱신 API는 만료된 Access Token과 독립적으로 Refresh Token을 검증한다.
        return Set.of("/auth/signup", "/auth/login", "/auth/refresh", "/auth/csrf").contains(path);
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
                sessions.validateSession(principal);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (CustomException exception) {
                SecurityContextHolder.clearContext();
                errors.writeError(response, exception.getErrorCode());
                return;
            } catch (DataAccessException exception) {
                errors.writeError(response, ErrorCode.INTERNAL_SERVER_ERROR);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
