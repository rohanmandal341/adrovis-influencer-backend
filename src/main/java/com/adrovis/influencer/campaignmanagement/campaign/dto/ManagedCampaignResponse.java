package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.fasterxml.jackson.databind.JsonNode;

public record ManagedCampaignResponse(
        UUID id,
        PublisherSummary publisher,
        UUID createdByAccountId,
        String title,
        String description,
        JsonNode details,
        CollaborationType collaborationType,
        CampaignStatus status,
        ApplicationMode applicationMode,
        String externalApplicationUrl,
        UUID publishedApplicationFormVersionId,
        Long version,
        Instant publishedAt,
        Instant closedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Management response only.
     * Public campaign browsing will use a separate, restricted DTO.
     */
    public static ManagedCampaignResponse from(ManagedCampaign campaign) {
        var publisher = campaign.getPublisherProfile();

        return new ManagedCampaignResponse(
                campaign.getId(),
                new PublisherSummary(
                        publisher.getId(),
                        publisher.getPublisherType(),
                        publisher.getDisplayName(),
                        publisher.getOwnerAccountId()
                ),
                campaign.getCreatedByAccountId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getDetails(),
                campaign.getCollaborationType(),
                campaign.getStatus(),
                campaign.getApplicationMode(),
                campaign.getExternalApplicationUrl(),
                campaign.getPublishedApplicationFormVersionId(),
                campaign.getVersion(),
                campaign.getPublishedAt(),
                campaign.getClosedAt(),
                campaign.getArchivedAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }

    public record PublisherSummary(
            UUID id,
            PublisherType type,
            String displayName,
            UUID ownerAccountId
    ) {
    }
}