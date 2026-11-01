package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.CampaignEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {

    @Query("SELECT c FROM CampaignEntity c LEFT JOIN FETCH c.images WHERE c.id = :id AND c.isActive = true")
    Optional<CampaignEntity> findActiveByIdWithImages(@Param("id") UUID id);

    Optional<CampaignEntity> findByApplicationFormUrl(String applicationFormUrl);

    @Modifying
    @Query("UPDATE CampaignEntity c SET c.viewsCount = c.viewsCount + 1 WHERE c.id = :id")
    void incrementViewsCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CampaignEntity c SET c.clicksCount = c.clicksCount + 1 WHERE c.id = :id")
    void incrementClicksCount(@Param("id") UUID id);

    @Query(value = """
        SELECT * FROM campaigns c
        WHERE c.is_active = true
          AND (:campaignType IS NULL OR c.campaign_type = :campaignType)
          AND (:maxFollowers IS NULL OR CAST(c.canonical_data->'eligibility'->>'minimum_followers' AS INTEGER) <= :maxFollowers)
          AND (:brand IS NULL OR LOWER(c.brand_name) LIKE LOWER(CONCAT('%', :brand, '%')))
          AND (:minPayout IS NULL OR (
                CAST(c.canonical_data->'compensation'->>'payout_amount_inr' AS NUMERIC) >= :minPayout
                OR CAST(c.canonical_data->'compensation'->'tiers'->0->>'payout' AS NUMERIC) >= :minPayout
              ))
        ORDER BY c.created_at DESC
        """,
        countQuery = """
        SELECT count(*) FROM campaigns c
        WHERE c.is_active = true
          AND (:campaignType IS NULL OR c.campaign_type = :campaignType)
          AND (:maxFollowers IS NULL OR CAST(c.canonical_data->'eligibility'->>'minimum_followers' AS INTEGER) <= :maxFollowers)
          AND (:brand IS NULL OR LOWER(c.brand_name) LIKE LOWER(CONCAT('%', :brand, '%')))
          AND (:minPayout IS NULL OR (
                CAST(c.canonical_data->'compensation'->>'payout_amount_inr' AS NUMERIC) >= :minPayout
                OR CAST(c.canonical_data->'compensation'->'tiers'->0->>'payout' AS NUMERIC) >= :minPayout
              ))
        """,
        nativeQuery = true)
    Page<CampaignEntity> findFilteredCampaigns(
            @Param("campaignType") String campaignType,
            @Param("maxFollowers") Integer maxFollowers,
            @Param("brand") String brand,
            @Param("minPayout") Double minPayout,
            Pageable pageable
    );
}
