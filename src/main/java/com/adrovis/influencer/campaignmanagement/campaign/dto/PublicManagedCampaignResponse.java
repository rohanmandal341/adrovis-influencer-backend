package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;

public record PublicManagedCampaignResponse(
        UUID id,
        String title,
        String description,
        PublisherSummary publisher,
        CollaborationType collaborationType,
        CampaignDetailsRequest details,
        ApplicationMode applicationMode,
        boolean acceptingApplications,
        Instant publishedAt
) {

    public record PublisherSummary(
            UUID id,
            PublisherType type,
            String displayName
    ) {
    }
}