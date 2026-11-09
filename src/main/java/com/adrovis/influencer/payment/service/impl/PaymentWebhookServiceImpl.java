package com.adrovis.influencer.payment.service.impl;

import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final RazorpayWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.max-webhook-bytes:262144}")
    private int maxWebhookBytes = 262144;

    /** Provider callbacks use signed raw bodies, not browser JWTs; all state changes remain transactional. */
    @Override
    @Transactional("primaryTransactionManager")
    public void processWebhook(String payload, String signature) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > maxWebhookBytes) {
            throw new PaymentOperationException(413, "WEBHOOK_TOO_LARGE", "Webhook payload exceeds the allowed size.");
        }
        if (!webhookVerifier.verifySignature(payload, signature)) {
            throw new PaymentOperationException(401, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Webhook payload is invalid.");
        }
        if (root == null || !root.isObject() || !root.path("event").isTextual()
                || root.path("event").asText().isBlank() || root.path("event").asText().length() > 100) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Webhook event type is missing or invalid.");
        }
        String eventType = root.path("event").asText();
        // Hash the signed body, not an unsigned request ID; identical retries remain idempotent across instances.
        String eventId = "sha256:" + hash(payload);
        if (webhookEventRepository.claim(UUID.randomUUID(), eventId, eventType, payload) == 0) {
            return;
        }
        if (!"payment.captured".equals(eventType)) {
            return; // Preserve the original supported event scope; other signed events are retained for audit.
        }
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String orderId = payment.path("order_id").asText();
        String paymentId = payment.path("id").asText();
        // Do not coerce numeric/structured identifiers into order lookups or persist unbounded provider IDs.
        if (!payment.path("order_id").isTextual() || !payment.path("id").isTextual()
                || orderId.isBlank() || paymentId.isBlank() || orderId.length() > 100 || paymentId.length() > 100
                || !payment.path("amount").isIntegralNumber()) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Captured payment fields are invalid.");
        }
        var transaction = transactionRepository.findLockedByRazorpayOrderId(orderId)
                .orElseThrow(() -> new PaymentOperationException(503, "PAYMENT_NOT_READY", "Payment order is not ready."));
        // A valid provider signature does not justify crediting a different amount, currency or order.
        BigDecimal paidPaise = new BigDecimal(payment.path("amount").asText());
        if (transaction.getAmount().multiply(BigDecimal.valueOf(100)).compareTo(paidPaise) != 0
                || !transaction.getCurrency().equals(payment.path("currency").asText())) {
            throw new PaymentOperationException(409, "PAYMENT_AMOUNT_MISMATCH", "Payment details do not match the order.");
        }
        if (transaction.getStatus() == PaymentStatus.REFUNDED) {
            return; // A delayed captured notification must never undo a recorded refund.
        }
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            if (!paymentId.equals(transaction.getRazorpayPaymentId())) {
                throw new PaymentOperationException(409, "PAYMENT_STATE_CONFLICT", "Payment state conflicts with the event.");
            }
            return;
        }
        transaction.markSuccess(paymentId, "WEBHOOK_VERIFIED");
        transactionRepository.save(transaction);
    }

    /** Creates a bounded deterministic retry key without logging or exposing the provider payload. */
    private String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required");
        }
    }
}
