package com.adrovis.influencer.payment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.AdminPaymentTransactionResponse;
import com.adrovis.influencer.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor

public class AdminPaymentController {

    private final PaymentService paymentService;
    /** Returns platform-wide payment statistics only to Admin accounts. */

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminPaymentStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getAdminStats()));
    }
    /** Keeps existing search/pagination while mapping internal records to a signature-free Admin DTO. */

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<AdminPaymentTransactionResponse>>> listTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminPaymentTransactionResponse> transactions = paymentService.getAllTransactions(search, pageable)
                .map(AdminPaymentTransactionResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
