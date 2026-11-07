package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    boolean existsByEventId(String eventId);
}
