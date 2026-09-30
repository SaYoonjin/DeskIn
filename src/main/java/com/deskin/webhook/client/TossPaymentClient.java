package com.deskin.webhook.client;

public interface TossPaymentClient {
    TossPaymentStatus getPaymentStatus(String paymentKey);
}
