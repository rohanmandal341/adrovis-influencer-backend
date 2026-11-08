package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);
    /** Serializes checkout and webhook updates so a late callback cannot overwrite another payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.razorpayOrderId = :orderId")
    Optional<PaymentTransaction> findLockedByRazorpayOrderId(@Param("orderId") String orderId);

    Optional<PaymentTransaction> findByReferenceId(String referenceId);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenue();

    Page<PaymentTransaction> findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(
            String email, String planId, Pageable pageable
    );
}
