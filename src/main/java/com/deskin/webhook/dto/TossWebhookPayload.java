package com.deskin.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 이벤트 종류마다 본문이 달라 data 검증은 결제 이벤트일 때 서비스에서 한다.
public record TossWebhookPayload(String eventType, String createdAt, Data data) {
    public record Data(@NotBlank @Size(max = 200) String paymentKey, @NotBlank String orderId,
                        @NotBlank String status, @NotNull Long totalAmount,
                        @Size(max = 64) String lastTransactionKey) {}
}
