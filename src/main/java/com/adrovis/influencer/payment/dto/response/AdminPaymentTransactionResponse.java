package com.adrovis.influencer.payment.dto.response;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Preserves admin table fields while excluding the gateway signature and exposing reviewed account ownership. */
public record AdminPaymentTransactionResponse(UUID id, UUID accountId, String referenceId, String email,
        String fullName, String planId, String userRole, BigDecimal amount, String currency, PaymentStatus status,
        String razorpayOrderId, String razorpayPaymentId, Instant createdAt, Instant updatedAt) {

    /** Maps internal persistence state to a deliberately limited API contract. */
    public static AdminPaymentTransactionResponse from(PaymentTransaction payment) {
        return new AdminPaymentTransactionResponse(payment.getId(), payment.getAccountId(), payment.getReferenceId(),
                payment.getEmail(), payment.getFullName(), payment.getPlanId(), payment.getUserRole(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getRazorpayOrderId(),
                payment.getRazorpayPaymentId(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
