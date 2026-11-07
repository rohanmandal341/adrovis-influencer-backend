package com.adrovis.influencer.payment.service.impl;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.entity.PaymentWebhookEvent;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final RazorpayWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void processWebhook(String payload, String signature) {
        if (!webhookVerifier.verifySignature(payload, signature)) {
            log.warn("Invalid Razorpay webhook signature received.");
            throw new SecurityException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.path("event").asText() + "_" + root.path("created_at").asLong() + "_" + root.path("payload").path("payment").path("entity").path("id").asText();

            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate webhook event received and ignored: {}", eventId);
                return;
            }

            PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(root.path("event").asText())
                    .payload(payload)
                    .build();
            webhookEventRepository.save(event);

            String eventType = root.path("event").asText();
            if ("payment.captured".equals(eventType)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String orderId = paymentEntity.path("order_id").asText();
                String paymentId = paymentEntity.path("id").asText();

                transactionRepository.findByRazorpayOrderId(orderId).ifPresent(tx -> {
                    if (tx.getStatus() != com.adrovis.influencer.payment.enums.PaymentStatus.SUCCESS) {
                        tx.markSuccess(paymentId, "WEBHOOK_VERIFIED");
                        transactionRepository.save(tx);
                        log.info("Webhook processed: payment captured for order ID {}", orderId);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to parse or process Razorpay webhook payload", e);
            throw new RuntimeException("Webhook processing error", e);
        }
    }
}
