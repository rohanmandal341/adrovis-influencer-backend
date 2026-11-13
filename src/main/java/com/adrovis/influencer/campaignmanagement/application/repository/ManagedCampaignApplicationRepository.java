package com.adrovis.influencer.campaignmanagement.application.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedCampaignApplicationRepository
        extends JpaRepository<ManagedCampaignApplication, UUID> {

    // Prevents duplicate applications by the same creator.
    boolean existsByCampaignIdAndCreatorAccountId(
            UUID campaignId,
            UUID creatorAccountId
    );

    // Lists only the authenticated creator's applications.
    Page<ManagedCampaignApplication> findAllByCreatorAccountId(
            UUID creatorAccountId,
            Pageable pageable
    );

    // Prevents creators from reading another creator's application.
    Optional<ManagedCampaignApplication> findByIdAndCreatorAccountId(
            UUID id,
            UUID creatorAccountId
    );

    Page<ManagedCampaignApplication> findAllByCampaignId(
            UUID campaignId,
            Pageable pageable
    );

    Page<ManagedCampaignApplication> findAllByCampaignIdAndStatus(
            UUID campaignId,
            CampaignApplicationStatus status,
            Pageable pageable
    );

    Optional<ManagedCampaignApplication> findByIdAndCampaignId(
            UUID id,
            UUID campaignId
    );

    /**
     * Caller must authorize and lock the parent campaign first.
     * Campaign ID prevents reviewing an application through the wrong campaign URL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select application
        from ManagedCampaignApplication application
        where application.id = :applicationId
          and application.campaignId = :campaignId
        """)
    Optional<ManagedCampaignApplication> findForReview(
            @Param("applicationId") UUID applicationId,
            @Param("campaignId") UUID campaignId
    );
}