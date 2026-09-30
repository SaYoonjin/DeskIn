package com.deskin.webhook;

import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.webhook.client.TossPaymentClient;
import com.deskin.webhook.client.TossPaymentStatus;
import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.impl.WebhookServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    @Mock WebhookEventRepository webhookEventRepository;
    @Mock TossPaymentClient tossPaymentClient;

    @Test
    void savesNewEventWhenNotAlreadyProcessedAndVerified() {
        var service = service();
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndLastTransactionKey("key_1", "DONE", "txn_1"))
                .thenReturn(false);
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "DONE", 35000L, "txn_1"));

        service.receiveTossWebhook(payload("key_1", "DONE", "txn_1"));

        verify(webhookEventRepository).saveAndFlush(any(WebhookEvent.class));
    }

    @Test
    void skipsVerificationAndSaveWhenAlreadyProcessed() {
        var service = service();
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndLastTransactionKey("key_1", "DONE", "txn_1"))
                .thenReturn(true);

        service.receiveTossWebhook(payload("key_1", "DONE", "txn_1"));

        verifyNoInteractions(tossPaymentClient);
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void treatsMissingTransactionKeyAsEmptyOnBothSides() {
        var service = service();
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndLastTransactionKey("key_1", "DONE", ""))
                .thenReturn(false);
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "DONE", 35000L, null));

        service.receiveTossWebhook(payload("key_1", "DONE", null));

        verify(webhookEventRepository).saveAndFlush(any(WebhookEvent.class));
    }

    @Test
    void rejectsWebhookWhenTossLookupDoesNotMatch() {
        var service = service();
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "CANCELED", 35000L, "txn_1"));

        assertVerificationFails(service, payload("key_1", "DONE", "txn_1"));
    }

    @Test
    void rejectsWebhookWhenTransactionKeyDoesNotMatch() {
        var service = service();
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "DONE", 35000L, "txn_other"));

        assertVerificationFails(service, payload("key_1", "DONE", "txn_1"));
    }

    @Test
    void rejectsWebhookWhenTossLookupFails() {
        var service = service();
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenThrow(new ResourceAccessException("connection failed"));

        assertVerificationFails(service, payload("key_1", "DONE", "txn_1"));
    }

    @Test
    void ignoresSaveConflictWhenEventNowExists() {
        var service = service();
        stubVerifiedLookup();
        when(webhookEventRepository.existsByPaymentKeyAndStatusAndLastTransactionKey("key_1", "DONE", "txn_1"))
                .thenReturn(false, true);
        when(webhookEventRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatNoException().isThrownBy(() -> service.receiveTossWebhook(payload("key_1", "DONE", "txn_1")));
    }

    @Test
    void rethrowsSaveFailureWhenEventStillMissing() {
        var service = service();
        stubVerifiedLookup();
        var exception = new DataIntegrityViolationException("value too long");
        when(webhookEventRepository.saveAndFlush(any())).thenThrow(exception);

        assertThatThrownBy(() -> service.receiveTossWebhook(payload("key_1", "DONE", "txn_1")))
                .isSameAs(exception);
    }

    @Test
    void ignoresOtherEventTypesWithoutTouchingTossOrDb() {
        var service = service();
        var payload = new TossWebhookPayload("DEPOSIT_CALLBACK", null, null);

        assertThatNoException().isThrownBy(() -> service.receiveTossWebhook(payload));

        verifyNoInteractions(tossPaymentClient, webhookEventRepository);
    }

    @Test
    void rejectsPaymentEventWithoutValidData() {
        var service = service();
        var noData = new TossWebhookPayload("PAYMENT_STATUS_CHANGED", null, null);
        var blankKey = new TossWebhookPayload("PAYMENT_STATUS_CHANGED", null,
                new TossWebhookPayload.Data("", "ORDER_1", "DONE", 35000L, "txn_1"));

        for (var payload : new TossWebhookPayload[]{noData, blankKey}) {
            assertThatThrownBy(() -> service.receiveTossWebhook(payload))
                    .isInstanceOfSatisfying(CustomException.class,
                            exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        verifyNoInteractions(tossPaymentClient, webhookEventRepository);
    }

    private WebhookServiceImpl service() {
        return new WebhookServiceImpl(webhookEventRepository, tossPaymentClient,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    private void stubVerifiedLookup() {
        when(tossPaymentClient.getPaymentStatus("key_1"))
                .thenReturn(new TossPaymentStatus("key_1", "ORDER_1", "DONE", 35000L, "txn_1"));
    }

    private void assertVerificationFails(WebhookServiceImpl service, TossWebhookPayload payload) {
        assertThatThrownBy(() -> service.receiveTossWebhook(payload))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEBHOOK_VERIFICATION_FAILED));
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    private TossWebhookPayload payload(String paymentKey, String status, String lastTransactionKey) {
        return new TossWebhookPayload("PAYMENT_STATUS_CHANGED", "2026-08-28T11:35:20.123456",
                new TossWebhookPayload.Data(paymentKey, "ORDER_1", status, 35000L, lastTransactionKey));
    }
}
