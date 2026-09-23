package com.deskin.webhook.repository;

import com.deskin.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByPaymentKeyAndStatusAndTotalAmount(String paymentKey, String status, Long totalAmount);
}
