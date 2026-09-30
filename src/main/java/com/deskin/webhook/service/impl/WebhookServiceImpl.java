package com.deskin.webhook.service.impl;

import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.WebhookService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {
    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    private final WebhookEventRepository webhookEventRepository;
    private final TossPaymentClient tossPaymentClient;
    private final Validator validator;

    // 이 메서드는 트랜잭션이 없어야 한다. saveAndFlush가 자기 트랜잭션에서 롤백된 뒤 예외를 여기서 처리해야
    // 중복 예외를 삼켜도 rollback-only 트랜잭션이 커밋되며 UnexpectedRollbackException이 나지 않는다.
    @Override
    public void receiveTossWebhook(TossWebhookPayload payload) {
        // 다른 이벤트는 본문 구조가 달라 처리하지 않는다. 400을 주면 Toss가 재전송하므로 200으로 무시한다.
        if (!PAYMENT_STATUS_CHANGED.equals(payload.eventType())) {
            return;
        }
        TossWebhookPayload.Data data = payload.data();
        if (data == null || !validator.validate(data).isEmpty()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        String transactionKey = normalize(data.lastTransactionKey());
        // 동일 결제·상태·거래(부분 취소 등) 재전송은 무시해 결제 상태 변경이 한 번만 반영되도록 한다.
        if (alreadyProcessed(data, transactionKey)) {
            return;
        }
        // Webhook 데이터를 그대로 신뢰하지 않고 PG의 결제 조회 API로 최종 상태를 재확인한다.
        verifyAgainstToss(data, transactionKey);
        try {
            webhookEventRepository.saveAndFlush(new WebhookEvent(payload.eventType(), data.paymentKey(),
                    data.orderId(), data.status(), data.totalAmount(), transactionKey));
        } catch (DataIntegrityViolationException exception) {
            // 동시 재전송이 사전 조회를 함께 통과해 유니크 제약을 뒤늦게 위반한 경우는 이미 처리된 이벤트다.
            // 상대 트랜잭션이 커밋된 뒤에 충돌이 나므로 다시 조회하면 보이고, 안 보이면 다른 제약 위반이다.
            if (!alreadyProcessed(data, transactionKey)) {
                throw exception;
            }
        }
    }

    // 거래 키가 없는 이벤트도 유니크 제약(NULL은 서로 다른 값으로 취급)으로 중복이 걸러지도록 빈 문자열로 통일한다.
    private String normalize(String transactionKey) {
        return transactionKey == null ? "" : transactionKey;
    }

    private void verifyAgainstToss(TossWebhookPayload.Data data, String transactionKey) {
        TossPaymentStatus actual;
        try {
            actual = tossPaymentClient.getPaymentStatus(data.paymentKey());
        } catch (RestClientException exception) {
            // Toss 조회 실패(네트워크 오류, 미존재 paymentKey 등)도 검증 실패로 취급해 400으로 응답한다.
            throw new CustomException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
        boolean matches = actual != null && data.orderId().equals(actual.orderId())
                && data.status().equals(actual.status()) && data.totalAmount().equals(actual.totalAmount())
                && transactionKey.equals(normalize(actual.lastTransactionKey()));
        if (!matches) {
            throw new CustomException(ErrorCode.WEBHOOK_VERIFICATION_FAILED);
        }
    }

    private boolean alreadyProcessed(TossWebhookPayload.Data data, String transactionKey) {
        return webhookEventRepository.existsByPaymentKeyAndStatusAndLastTransactionKey(
                data.paymentKey(), data.status(), transactionKey);
    }
}
