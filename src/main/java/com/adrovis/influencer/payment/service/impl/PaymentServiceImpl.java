package com.adrovis.influencer.payment.service.impl;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.service.PaymentService;
import com.razorpay.Order;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final RazorpayClientWrapper razorpayClient;
    private final RazorpayProperties razorpayProperties;

    @Override
    @Transactional
    public PaymentResponse createOrder(CreateOrderRequest request) {
        try {
            String referenceId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            Order razorpayOrder = razorpayClient.createOrder(request.getAmount(), referenceId);
            String razorpayOrderId = razorpayOrder.get("id");

            PaymentTransaction tx = PaymentTransaction.builder()
                    .referenceId(referenceId)
                    .email(request.getEmail().trim().toLowerCase())
                    .fullName(request.getFullName())
                    .planId(request.getPlanId())
                    .userRole(request.getUserRole().toUpperCase())
                    .amount(request.getAmount())
                    .currency("INR")
                    .status(PaymentStatus.INITIATED)
                    .razorpayOrderId(razorpayOrderId)
                    .build();

            transactionRepository.save(tx);
            log.info("Created payment order {} for email {}", razorpayOrderId, request.getEmail());

            return PaymentResponse.from(tx, razorpayProperties.getKeyId());
        } catch (Exception e) {
            log.error("Failed to create Razorpay order", e);
            throw new RuntimeException("Failed to initiate payment order: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        PaymentTransaction tx = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment transaction not found for order ID: " + request.getRazorpayOrderId()));

        try {
            // Construct the JSONObject required by Razorpay SDK
            org.json.JSONObject options = new org.json.JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            // Verify signature using the SDK's expected signature
            boolean isValid = com.razorpay.Utils.verifyPaymentSignature(options, razorpayProperties.getKeySecret());

            if (!isValid) {
                tx.markFailed();
                transactionRepository.save(tx);
                throw new SecurityException("Invalid payment signature verification failed.");
            }

            tx.markSuccess(request.getRazorpayPaymentId(), request.getRazorpaySignature());
            transactionRepository.save(tx);
            log.info("Payment verified successfully for order ID {}", request.getRazorpayOrderId());

            return PaymentResponse.from(tx, razorpayProperties.getKeyId());
        } catch (Exception e) {
            log.error("Payment verification failed", e);
            throw new RuntimeException("Payment verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentStatsResponse getAdminStats() {
        long total = transactionRepository.count();
        long success = transactionRepository.countByStatus(PaymentStatus.SUCCESS);
        long initiated = transactionRepository.countByStatus(PaymentStatus.INITIATED);
        long failed = transactionRepository.countByStatus(PaymentStatus.FAILED);
        BigDecimal revenue = transactionRepository.sumTotalRevenue();

        return AdminPaymentStatsResponse.builder()
                .totalTransactions(total)
                .successTransactions(success)
                .initiatedTransactions(initiated)
                .failedTransactions(failed)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return transactionRepository.findAll(pageable);
        }
        return transactionRepository.findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(search.trim(), search.trim(), pageable);
    }
}
