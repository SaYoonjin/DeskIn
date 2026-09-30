package com.deskin.webhook.service;

import com.deskin.webhook.dto.TossWebhookPayload;

public interface WebhookService {
    void receiveTossWebhook(TossWebhookPayload payload);
}
