package com.deskin.webhook.service;

import com.deskin.webhook.entity.WebhookEvent;
import com.deskin.webhook.repository.WebhookEventRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WebhookEventPersister {
    private final WebhookEventRepository webhookEventRepository;

    // 별도 트랜잭션(REQUIRES_NEW)으로 분리해, flush 실패로 무효화된 영속성 컨텍스트가
    // 호출자의 트랜잭션까지 오염시키지 않도록 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveIfNew(WebhookEvent event) {
        try {
            webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateEventConstraint(exception)) {
                throw exception;
            }
            // 동시 재전송으로 유니크 제약을 두 번째로 위반한 요청은 이미 처리된 이벤트로 간주한다.
        }
    }

    private boolean isDuplicateEventConstraint(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT)
                            .contains("uk_webhook_events_payment_key_status_amount")) {
                return true;
            }
        }
        return false;
    }
}
