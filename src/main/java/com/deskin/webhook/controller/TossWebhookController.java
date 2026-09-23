package com.deskin.webhook.controller;

import com.deskin.webhook.dto.TossWebhookPayload;
import com.deskin.webhook.dto.TossWebhookResponse;
import com.deskin.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class TossWebhookController {
    private final WebhookService webhookService;

    @PostMapping("/toss")
    public TossWebhookResponse receiveTossWebhook(@Valid @RequestBody TossWebhookPayload payload) {
        webhookService.receiveTossWebhook(payload);
        return new TossWebhookResponse(true);
    }
}
