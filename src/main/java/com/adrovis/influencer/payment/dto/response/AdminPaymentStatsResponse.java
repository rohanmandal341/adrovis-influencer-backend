package com.adrovis.influencer.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminPaymentStatsResponse {
    private long totalTransactions;
    private long successTransactions;
    private long initiatedTransactions;
    private long failedTransactions;
    private BigDecimal totalRevenue;
}
