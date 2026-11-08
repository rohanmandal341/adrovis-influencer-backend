package com.adrovis.influencer.payment.service;

import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    /** Requires a verified caller; ownership and contact identity come from server authentication. */
    PaymentResponse createOrder(CreateOrderRequest request);
    /** Verifies only a caller-owned order; another account cannot submit its payment callback. */
    PaymentResponse verifyPayment(VerifyPaymentRequest request);
    /** Platform-wide financial reporting requires ADMIN at the implementation boundary. */
    AdminPaymentStatsResponse getAdminStats();
    /** Internal read model for Admin reporting; controllers map it to a signature-free DTO. */
    Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable);
}
