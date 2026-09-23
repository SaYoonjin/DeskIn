package com.deskin.webhook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TossWebhookPayload(@NotBlank String eventType, String createdAt, @Valid @NotNull Data data) {
    public record Data(@NotBlank String paymentKey, @NotBlank String orderId, @NotBlank String status,
                        @NotNull Long totalAmount) {}
}
