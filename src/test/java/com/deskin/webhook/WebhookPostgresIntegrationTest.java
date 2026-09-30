package com.deskin.webhook;

import com.deskin.support.PostgresIntegrationTest;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.WebhookService;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class WebhookPostgresIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebhookService webhookService;
    @Autowired WebhookEventRepository webhookEventRepository;
    @MockBean TossPaymentClient tossPaymentClient;

    @BeforeEach
    void clean() {
        webhookEventRepository.deleteAll();
    }

    @Test
    void concurrentDuplicateWebhooksStoreOnceAndBothSucceed() throws Exception {
        // 두 요청이 사전 조회를 함께 통과한 뒤 유니크 제약으로 충돌하는 상황을 재현한다.
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(tossPaymentClient.getPaymentStatus(anyString())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new TossPaymentStatus("key_race", "ORDER_1", "DONE", 35000L, "txn_1");
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(
                    () -> receive("key_race", "DONE", "txn_1"),
                    () -> receive("key_race", "DONE", "txn_1")), 30, TimeUnit.SECONDS);
            // 예외 없이 끝나야 하므로 get()이 던지는지 함께 확인한다.
            for (var result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(webhookEventRepository.count()).isEqualTo(1);
    }

    @Test
    void storesConsecutivePartialCancelsButDeduplicatesResends() {
        stubToss("key_1", "PARTIAL_CANCELED", "txn_1");
        webhookService.receiveTossWebhook(payload("key_1", "PARTIAL_CANCELED", "txn_1"));
        stubToss("key_1", "PARTIAL_CANCELED", "txn_2");
        webhookService.receiveTossWebhook(payload("key_1", "PARTIAL_CANCELED", "txn_2"));
        assertThat(webhookEventRepository.count()).isEqualTo(2);

        // 같은 이벤트 재전송은 조회·저장 없이 무시된다.
        webhookService.receiveTossWebhook(payload("key_1", "PARTIAL_CANCELED", "txn_2"));

        assertThat(webhookEventRepository.count()).isEqualTo(2);
    }

    @Test
    void storesPaymentKeyOfMaxLength() {
        String paymentKey = "k".repeat(200);
        stubToss(paymentKey, "DONE", "txn_1");

        webhookService.receiveTossWebhook(payload(paymentKey, "DONE", "txn_1"));

        assertThat(webhookEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getPaymentKey()).hasSize(200));
    }

    private Object receive(String paymentKey, String status, String transactionKey) {
        webhookService.receiveTossWebhook(payload(paymentKey, status, transactionKey));
        return "OK";
    }

    private void stubToss(String paymentKey, String status, String transactionKey) {
        when(tossPaymentClient.getPaymentStatus(paymentKey))
                .thenReturn(new TossPaymentStatus(paymentKey, "ORDER_1", status, 35000L, transactionKey));
    }

    private TossWebhookPayload payload(String paymentKey, String status, String transactionKey) {
        return new TossWebhookPayload("PAYMENT_STATUS_CHANGED", "2026-08-28T11:35:20.123456",
                new TossWebhookPayload.Data(paymentKey, "ORDER_1", status, 35000L, transactionKey));
    }
}
