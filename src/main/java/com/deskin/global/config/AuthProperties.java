package com.deskin.global.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@NotNull Duration sessionDuration, boolean cookieSecure,
                             @NotEmpty List<String> allowedOrigins) {
    public AuthProperties {
        if (sessionDuration != null && (sessionDuration.isZero() || sessionDuration.isNegative())) {
            throw new IllegalArgumentException("로그인 유지 기간은 양수여야 합니다.");
        }
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("허용 Origin에는 와일드카드를 사용할 수 없습니다.");
        }
    }
}
