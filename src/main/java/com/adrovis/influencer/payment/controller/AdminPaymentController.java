package com.adrovis.influencer.payment.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminPaymentStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getAdminStats()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<PaymentTransaction>>> listTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> transactions = paymentService.getAllTransactions(search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
