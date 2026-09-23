package com.deskin.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String clientKey, @NotBlank String baseUrl) {
}
