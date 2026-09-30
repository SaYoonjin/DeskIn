package com.deskin.webhook.repository;

import com.deskin.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByPaymentKeyAndStatusAndLastTransactionKey(String paymentKey, String status,
                                                             String lastTransactionKey);
}
