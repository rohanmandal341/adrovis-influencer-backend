package com.adrovis.influencer.payment.service;

import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    PaymentResponse createOrder(CreateOrderRequest request);
    PaymentResponse verifyPayment(VerifyPaymentRequest request);
    AdminPaymentStatsResponse getAdminStats();
    Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable);
}
