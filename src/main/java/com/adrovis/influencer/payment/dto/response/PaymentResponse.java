package com.adrovis.influencer.payment.dto.response;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private String referenceId;
    private String email;
    private String planId;
    private String userRole;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpayKeyId;
    private Instant createdAt;
    /** Maps persistence state into the explicitly defined response fields. */

    public static PaymentResponse from(PaymentTransaction tx, String razorpayKeyId) {
        return PaymentResponse.builder()
                .id(tx.getId())
                .referenceId(tx.getReferenceId())
                .email(tx.getEmail())
                .planId(tx.getPlanId())
                .userRole(tx.getUserRole())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .razorpayOrderId(tx.getRazorpayOrderId())
                .razorpayPaymentId(tx.getRazorpayPaymentId())
                .razorpayKeyId(razorpayKeyId)
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
