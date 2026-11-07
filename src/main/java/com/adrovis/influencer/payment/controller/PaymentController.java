package com.adrovis.influencer.payment.controller;

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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        PaymentResponse response = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment order created successfully", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        PaymentResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully", response));
    }
}
