package com.deskin.webhook.entity;

import com.deskin.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_webhook_events_payment_key_status_amount",
                columnNames = {"payment_key", "status", "total_amount"})
})
public class WebhookEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payment_key", nullable = false, length = 100)
    private String paymentKey;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    public WebhookEvent(String eventType, String paymentKey, String orderId, String status, Long totalAmount) {
        this.eventType = eventType;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
    }
}
