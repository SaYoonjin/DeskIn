package com.deskin.global.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@NotNull Duration refreshTokenDuration,
                             @NotEmpty List<String> allowedOrigins,
                             @Min(1) int rateLimitMaxAttempts,
                             @NotNull Duration rateLimitWindow) {
    public AuthProperties {
        if (refreshTokenDuration != null && (refreshTokenDuration.isZero() || refreshTokenDuration.isNegative())) {
            throw new IllegalArgumentException("Refresh Token 유지 기간은 양수여야 합니다.");
        }
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("허용 Origin에는 와일드카드를 사용할 수 없습니다.");
        }
        if (rateLimitWindow != null && (rateLimitWindow.isZero() || rateLimitWindow.isNegative())) {
            throw new IllegalArgumentException("Rate limit 기간은 양수여야 합니다.");
        }
    }
}
