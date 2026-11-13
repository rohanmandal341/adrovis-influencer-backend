package com.adrovis.influencer.campaignmanagement.campaign.repository;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;

public interface ManagedCampaignRepository
        extends JpaRepository<ManagedCampaign, UUID> {

    // Brand/agency dashboard: campaigns owned by this account only.
    @EntityGraph(attributePaths = "publisherProfile")
    Page<ManagedCampaign>
    findAllByPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
            UUID ownerAccountId,
            Pageable pageable
    );

    // Admin dashboard: all non-archived campaigns.
    @EntityGraph(attributePaths = "publisherProfile")
    Page<ManagedCampaign> findAllByArchivedAtIsNull(Pageable pageable);

    // Ownership-scoped retrieval for brand/agency operations.
    @EntityGraph(attributePaths = "publisherProfile")
    Optional<ManagedCampaign>
    findByIdAndPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
            UUID id,
            UUID ownerAccountId
    );

    // Admin retrieval. The service must explicitly authorize the admin first.
    @EntityGraph(attributePaths = "publisherProfile")
    Optional<ManagedCampaign> findByIdAndArchivedAtIsNull(UUID id);

    /**
     * Locks an owner's campaign while its next form version is allocated.
     * The ownership restriction is part of the database query.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.archivedAt is null
          and campaign.publisherProfile.ownerAccountId = :ownerAccountId
        """)
    Optional<ManagedCampaign> findOwnedForFormUpdate(
            @Param("campaignId") UUID campaignId,
            @Param("ownerAccountId") UUID ownerAccountId
    );

    /**
     * Admin-only service path: locks any non-archived campaign.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.archivedAt is null
        """)
    Optional<ManagedCampaign> findForFormUpdate(
            @Param("campaignId") UUID campaignId
    );


    /**
     * Public listing excludes drafts, paused/closed/archived campaigns
     * and campaigns whose publisher has been archived.
     */
    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Page<ManagedCampaign> findPublicCampaigns(
            @Param("status") CampaignStatus status,
            Pageable pageable
    );

    /**
     * Public lookup must not reveal draft or unrelated management data.
     */
    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Optional<ManagedCampaign> findPublicCampaign(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignStatus status
    );

    /**
     * Serializes submissions with lifecycle changes on the same campaign.
     * Draft/private/archived campaigns remain concealed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Optional<ManagedCampaign> findPublicForApplication(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignStatus status
    );
}