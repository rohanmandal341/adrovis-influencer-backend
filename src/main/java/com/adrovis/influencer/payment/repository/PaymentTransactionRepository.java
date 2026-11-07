package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentTransaction> findByReferenceId(String referenceId);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenue();

    Page<PaymentTransaction> findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(
            String email, String planId, Pageable pageable
    );
}
