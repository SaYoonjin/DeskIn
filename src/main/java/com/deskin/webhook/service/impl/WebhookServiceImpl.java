package com.deskin.webhook.service.impl;

import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.WebhookEventPersister;
import com.deskin.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventPersister webhookEventPersister;
    private final TossPaymentClient tossPaymentClient;

    @Override
    public void receiveTossWebhook(TossWebhookPayload payload) {
        TossWebhookPayload.Data data = payload.data();
        // 동일 paymentKey+status+금액 재전송은 무시해 결제 상태 변경이 한 번만 반영되도록 한다.
        if (webhookEventRepository.existsByPaymentKeyAndStatusAndTotalAmount(
                data.paymentKey(), data.status(), data.totalAmount())) {
            return;
        }
        // Webhook 데이터를 그대로 신뢰하지 않고 PG의 결제 조회 API로 최종 상태를 재확인한다.
        verifyAgainstToss(data);
        webhookEventPersister.saveIfNew(new WebhookEvent(payload.eventType(), data.paymentKey(),
                data.orderId(), data.status(), data.totalAmount()));
    }

    private void verifyAgainstToss(TossWebhookPayload.Data data) {
        TossPaymentStatus actual;
        try {
            actual = tossPaymentClient.getPaymentStatus(data.paymentKey());
        } catch (RestClientException exception) {
            // Toss 조회 실패(네트워크 오류, 미존재 paymentKey 등)도 검증 실패로 취급해 400으로 응답한다.
            throw new CustomException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
        boolean matches = actual != null && data.orderId().equals(actual.orderId())
                && data.status().equals(actual.status()) && data.totalAmount().equals(actual.totalAmount());
        if (!matches) {
            throw new CustomException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
    }
}
