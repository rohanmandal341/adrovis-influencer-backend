package com.adrovis.influencer.campaignmanagement.dashboard.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CampaignDashboardRepository
        extends Repository<ManagedCampaign, UUID> {

    interface CampaignCount {
        CampaignStatus getStatus();
        long getTotal();
    }

    interface ApplicationCount {
        CampaignApplicationStatus getStatus();
        long getTotal();
    }

    /**
     * Counts include historical/archived records.
     * Ownership scope is always derived by the service.
     */
    @Query("""
            select campaign.status as status, count(campaign) as total
            from ManagedCampaign campaign
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            group by campaign.status
            """)
    List<CampaignCount> countCampaigns(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes
    );

    @Query("""
            select application.status as status, count(application) as total
            from ManagedCampaignApplication application
            join ManagedCampaign campaign
                on campaign.id = application.campaignId
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            group by application.status
            """)
    List<ApplicationCount> countApplications(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes
    );

    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
            select campaign
            from ManagedCampaign campaign
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            and (:includeArchived = true or campaign.archivedAt is null)
            and (:status is null or campaign.status = :status)
            and (:publisherType is null
                or publisher.publisherType = :publisherType)
            and (:publisherId is null or publisher.id = :publisherId)
            and (
                lower(campaign.title) like :search escape '!'
                or lower(publisher.displayName) like :search escape '!'
            )
            """)
    Page<ManagedCampaign> searchCampaigns(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes,
            @Param("includeArchived") boolean includeArchived,
            @Param("status") CampaignStatus status,
            @Param("publisherType") PublisherType publisherType,
            @Param("publisherId") UUID publisherId,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Parent campaign access must be authorized before this query.
     * Search supports verified email, account UUID, and conventional answer keys.
     */
    @Query("""
            select application
            from ManagedCampaignApplication application
            where application.campaignId = :campaignId
            and (:status is null or application.status = :status)
            and (
                lower(application.creatorEmail) like :search escape '!'
                or lower(cast(application.creatorAccountId as string))
                    like :search escape '!'
                or lower(coalesce(
                    cast(function(
                        'jsonb_extract_path_text',
                        application.answers,
                        'fullName'
                    ) as string), ''
                )) like :search escape '!'
                or lower(coalesce(
                    cast(function(
                        'jsonb_extract_path_text',
                        application.answers,
                        'instagramUrl'
                    ) as string), ''
                )) like :search escape '!'
            )
            """)
    Page<ManagedCampaignApplication> searchApplications(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignApplicationStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    interface ExportRow {
        String getApplicationId();
        String getCreatorAccountId();
        String getCreatorEmail();
        String getStatus();
        String getSubmittedAt();
        String getSubmittedName();
        String getInstagramUrl();
        String getGender();
        String getFollowerCount();
        String getPhone();
        String getPortfolioUrl();
    }

    /**
     * Projects only export columns instead of loading complete answer documents.
     * LIMIT is bounded by the service.
     */
    @Query(value = """
            SELECT
                CAST(a.id AS varchar) AS "applicationId",
                CAST(a.creator_account_id AS varchar) AS "creatorAccountId",
                a.creator_email AS "creatorEmail",
                a.status AS "status",
                to_char(
                    a.submitted_at AT TIME ZONE 'UTC',
                    'YYYY-MM-DD"T"HH24:MI:SS"Z"'
                ) AS "submittedAt",
                a.answers ->> 'fullName' AS "submittedName",
                a.answers ->> 'instagramUrl' AS "instagramUrl",
                a.answers ->> 'gender' AS "gender",
                a.answers ->> 'followerCount' AS "followerCount",
                a.answers ->> 'phone' AS "phone",
                a.answers ->> 'portfolioUrl' AS "portfolioUrl"
            FROM public.managed_campaign_applications a
            WHERE a.campaign_id = :campaignId
              AND (:status = '' OR a.status = :status)
              AND (
                  lower(a.creator_email) LIKE :search ESCAPE '!'
                  OR lower(CAST(a.creator_account_id AS varchar))
                      LIKE :search ESCAPE '!'
                  OR lower(coalesce(a.answers ->> 'fullName', ''))
                      LIKE :search ESCAPE '!'
                  OR lower(coalesce(a.answers ->> 'instagramUrl', ''))
                      LIKE :search ESCAPE '!'
              )
            ORDER BY a.submitted_at DESC, a.id DESC
            LIMIT :rowLimit
            """, nativeQuery = true)
    List<ExportRow> exportApplications(
            @Param("campaignId") UUID campaignId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("rowLimit") int rowLimit
    );
}