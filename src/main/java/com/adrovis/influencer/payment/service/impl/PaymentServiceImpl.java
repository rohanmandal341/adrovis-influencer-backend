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
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.security.CurrentAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CurrentAccount accounts;

    /** Preserves frontend pricing while binding every new order to a verified account and permitted role. */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Transactional("primaryTransactionManager")
    public PaymentResponse createOrder(CreateOrderRequest request) {
        var account = accounts.require();
        if (!account.email().equals(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new AccessDeniedException("Payment email must match the verified account");
        }
        String role = request.getUserRole().toUpperCase(java.util.Locale.ROOT);
        if (!account.roles().contains(role)) {
            throw new AccessDeniedException("The account does not have the requested role");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().stripTrailingZeros().scale() > 2) {
            throw new PaymentOperationException(400, "INVALID_AMOUNT", "Use a positive amount with two decimal places.");
        }
        String referenceId = "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        Order razorpayOrder;
        try {
            razorpayOrder = razorpayClient.createOrder(request.getAmount(), referenceId);
        } catch (com.razorpay.RazorpayException exception) {
            // Removed provider-message echo and email logging; those may contain credentials or personal data.
            log.warn("Payment provider did not create an order.");
            throw new PaymentOperationException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Unable to create the payment order.");
        }
        String orderId = razorpayOrder.get("id");
        if (orderId == null || orderId.isBlank()) {
            throw new PaymentOperationException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Unable to create the payment order.");
        }
        PaymentTransaction transaction = PaymentTransaction.builder()
                .accountId(account.id()).referenceId(referenceId).email(account.email())
                .fullName(request.getFullName()).planId(request.getPlanId()).userRole(role)
                .amount(request.getAmount()).currency("INR").status(PaymentStatus.INITIATED)
                .razorpayOrderId(orderId).build();
        transactionRepository.save(transaction);
        log.info("Created payment order: reference={}", referenceId);
        return PaymentResponse.from(transaction, razorpayProperties.getKeyId());
    }

    /** Checks ownership before signature processing; an invalid signature never changes payment state. */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Transactional("primaryTransactionManager")
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        var account = accounts.require();
        PaymentTransaction transaction = transactionRepository
                .findLockedByRazorpayOrderId(request.getRazorpayOrderId())
                .filter(payment -> account.id().equals(payment.getAccountId()))
                .orElseThrow(() -> new PaymentOperationException(404, "PAYMENT_NOT_FOUND", "Payment order not found."));
        org.json.JSONObject options = new org.json.JSONObject();
        // Use the stored order identifier, not a different client-provided order, in the signature check.
        options.put("razorpay_order_id", transaction.getRazorpayOrderId());
        options.put("razorpay_payment_id", request.getRazorpayPaymentId());
        options.put("razorpay_signature", request.getRazorpaySignature());
        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(options, razorpayProperties.getKeySecret());
        } catch (com.razorpay.RazorpayException exception) {
            valid = false;
        }
        if (!valid) {
            throw new PaymentOperationException(400, "INVALID_PAYMENT_SIGNATURE", "Payment signature is invalid.");
        }
        if (transaction.getStatus() == PaymentStatus.REFUNDED
                || (transaction.getStatus() == PaymentStatus.SUCCESS
                && !request.getRazorpayPaymentId().equals(transaction.getRazorpayPaymentId()))) {
            throw new PaymentOperationException(409, "PAYMENT_STATE_CONFLICT", "Payment state does not permit this update.");
        }
        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            // Preserve the existing checkout verification lifecycle; settlement reconciliation remains the webhook's job.
            transaction.markSuccess(request.getRazorpayPaymentId(), request.getRazorpaySignature());
            transactionRepository.save(transaction);
        }
        return PaymentResponse.from(transaction, razorpayProperties.getKeyId());
    }

    /** Platform-wide payment reporting is restricted in the service as well as the HTTP filter. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(value = "primaryTransactionManager", readOnly = true)
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

    /** Platform-wide payment reporting is restricted in the service as well as the HTTP filter. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return transactionRepository.findAll(pageable);
        }
        return transactionRepository.findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(search.trim(), search.trim(), pageable);
    }
}
