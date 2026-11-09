package com.adrovis.influencer.payment.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final PaymentWebhookService webhookService;
    /** Accepts provider-signed callbacks; user Bearer authentication is intentionally not the provider protocol. */

    @PostMapping
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        webhookService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully", "OK"));
    }
}
