package com.deskin.webhook.client;

public record TossPaymentStatus(String paymentKey, String orderId, String status, Long totalAmount) {
}
