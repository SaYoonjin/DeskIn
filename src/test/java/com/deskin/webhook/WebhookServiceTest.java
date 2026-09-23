package com.deskin.webhook;

import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.WebhookEventPersister;
import com.deskin.webhook.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    @Mock WebhookEventRepository webhookEventRepository;
    @Mock WebhookEventPersister webhookEventPersister;
    @Mock TossPaymentClient tossPaymentClient;

    @Test
    void savesNewEventWhenNotAlreadyProcessedAndVerified() {
        var service = new WebhookServiceImpl(webhookEventRepository, webhookEventPersister, tossPaymentClient);
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndTotalAmount("key_1", "DONE", 35000L))
                .thenReturn(false);
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "DONE", 35000L));

        service.receiveTossWebhook(payload("key_1", "DONE"));

        verify(webhookEventPersister).saveIfNew(any(WebhookEvent.class));
    }

    @Test
    void skipsVerificationAndSaveWhenAlreadyProcessed() {
        var service = new WebhookServiceImpl(webhookEventRepository, webhookEventPersister, tossPaymentClient);
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndTotalAmount("key_1", "DONE", 35000L))
                .thenReturn(true);

        service.receiveTossWebhook(payload("key_1", "DONE"));

        verifyNoInteractions(tossPaymentClient);
        verifyNoInteractions(webhookEventPersister);
    }

    @Test
    void rejectsWebhookWhenTossLookupDoesNotMatch() {
        var service = new WebhookServiceImpl(webhookEventRepository, webhookEventPersister, tossPaymentClient);
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndTotalAmount("key_1", "DONE", 35000L))
                .thenReturn(false);
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "CANCELED", 35000L));

        assertThatThrownBy(() -> service.receiveTossWebhook(payload("key_1", "DONE")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEBHOOK_VERIFICATION_FAILED));
        verifyNoInteractions(webhookEventPersister);
    }

    @Test
    void rejectsWebhookWhenTossLookupFails() {
        var service = new WebhookServiceImpl(webhookEventRepository, webhookEventPersister, tossPaymentClient);
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndTotalAmount("key_1", "DONE", 35000L))
                .thenReturn(false);
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenThrow(new ResourceAccessException("connection failed"));

        assertThatThrownBy(() -> service.receiveTossWebhook(payload("key_1", "DONE")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEBHOOK_VERIFICATION_FAILED));
        verifyNoInteractions(webhookEventPersister);
    }

    private TossWebhookPayload payload(String paymentKey, String status) {
        return new TossWebhookPayload("PAYMENT_STATUS_CHANGED", "2026-08-28T11:35:20.123456",
                new TossWebhookPayload.Data(paymentKey, "ORDER_1", status, 35000L));
    }
}
