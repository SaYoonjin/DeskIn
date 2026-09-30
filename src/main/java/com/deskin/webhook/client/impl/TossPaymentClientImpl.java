package com.deskin.webhook.client.impl;

import com.deskin.global.config.TossProperties;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TossPaymentClientImpl implements TossPaymentClient {
    private final RestClient restClient;

    public TossPaymentClientImpl(TossProperties properties) {
        String credentials = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials).build();
    }

    @Override
    public TossPaymentStatus getPaymentStatus(String paymentKey) {
        return restClient.get().uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve().body(TossPaymentStatus.class);
    }
}
