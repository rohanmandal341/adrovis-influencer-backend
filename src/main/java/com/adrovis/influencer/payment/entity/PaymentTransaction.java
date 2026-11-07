package com.adrovis.influencer.payment.entity;

import com.adrovis.influencer.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_order_id", columnList = "razorpay_order_id"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 64)
    private String referenceId;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "plan_id", nullable = false, length = 50)
    private String planId; // e.g. "brand_pro", "brand_unlimited", "creator_pro", "creator_elite"

    @Column(name = "user_role", nullable = false, length = 30)
    private String userRole; // "BRAND" or "CREATOR"

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // in INR (e.g. 250.00)

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "razorpay_order_id", unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markSuccess(String paymentId, String signature) {
        this.status = PaymentStatus.SUCCESS;
        this.razorpayPaymentId = paymentId;
        this.razorpaySignature = signature;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }
}
