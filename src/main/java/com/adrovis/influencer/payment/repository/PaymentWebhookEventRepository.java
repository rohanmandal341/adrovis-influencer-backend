package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    boolean existsByEventId(String eventId);

    /** A database unique constraint claims retries atomically; concurrent delivery cannot process twice. */
    @Modifying
    @Query(value = """
            INSERT INTO public.payment_webhook_events (id, event_id, event_type, payload, received_at)
            VALUES (:id, :eventId, :type, :payload, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("eventId") String eventId,
              @Param("type") String type, @Param("payload") String payload);
}
