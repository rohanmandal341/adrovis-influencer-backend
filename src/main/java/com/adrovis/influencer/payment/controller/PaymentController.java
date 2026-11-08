package com.adrovis.influencer.payment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor

public class PaymentController {

    private final PaymentService paymentService;
    /** Creates an authenticated account-owned order after DTO and role checks; pricing contract is unchanged. */

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        PaymentResponse response = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment order created successfully", response));
    }
    /** Allows only the account owner to verify an existing order through the signed checkout callback. */

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        PaymentResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully", response));
    }
}
