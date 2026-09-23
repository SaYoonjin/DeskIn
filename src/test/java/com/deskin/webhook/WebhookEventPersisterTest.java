package com.deskin.webhook;

import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import com.deskin.webhook.service.WebhookEventPersister;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookEventPersisterTest {
    @Mock WebhookEventRepository webhookEventRepository;

    @Test
    void savesWhenNoConflict() {
        var persister = new WebhookEventPersister(webhookEventRepository);

        persister.saveIfNew(event());

        verify(webhookEventRepository).saveAndFlush(any(WebhookEvent.class));
    }

    @Test
    void swallowsOnlyTheDuplicateEventConstraint() {
        var persister = new WebhookEventPersister(webhookEventRepository);
        var cause = new ConstraintViolationException("duplicate",
                new SQLException("duplicate key"), "uk_webhook_events_payment_key_status_amount");
        when(webhookEventRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", cause));

        assertThatNoException().isThrownBy(() -> persister.saveIfNew(event()));
    }

    @Test
    void rethrowsUnrelatedConstraintViolations() {
        var persister = new WebhookEventPersister(webhookEventRepository);
        var cause = new ConstraintViolationException("too long",
                new SQLException("value too long"), "some_other_constraint");
        var exception = new DataIntegrityViolationException("too long", cause);
        when(webhookEventRepository.saveAndFlush(any())).thenThrow(exception);

        assertThatThrownBy(() -> persister.saveIfNew(event())).isSameAs(exception);
    }

    private WebhookEvent event() {
        return new WebhookEvent("PAYMENT_STATUS_CHANGED", "key_1", "ORDER_1", "DONE", 35000L);
    }
}
