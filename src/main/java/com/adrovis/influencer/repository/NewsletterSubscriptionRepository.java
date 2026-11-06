package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.NewsletterSubscriptionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscriptionEntity, UUID> {

    Optional<NewsletterSubscriptionEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
        SELECT * FROM newsletter_subscriptions n
        WHERE (:activeOnly IS NULL OR n.is_active = :activeOnly)
          AND (:search IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY n.subscribed_at DESC
        """,
            countQuery = """
        SELECT count(*) FROM newsletter_subscriptions n
        WHERE (:activeOnly IS NULL OR n.is_active = :activeOnly)
          AND (:search IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
            nativeQuery = true)
    Page<NewsletterSubscriptionEntity> findFilteredSubscriptions(
            @Param("activeOnly") Boolean activeOnly,
            @Param("search") String search,
            Pageable pageable
    );
}