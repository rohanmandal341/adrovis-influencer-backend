package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record PublisherCampaignApplicationResponse(
        UUID id,
        UUID campaignId,
        UUID creatorAccountId,
        String creatorEmail,
        UUID formVersionId,
        int formVersion,
        CampaignApplicationStatus status,
        JsonNode answers,
        Instant submittedAt,
        Long version
) {

    public static PublisherCampaignApplicationResponse from(
            ManagedCampaignApplication application
    ) {
        return new PublisherCampaignApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getCreatorAccountId(),
                application.getCreatorEmail(),
                application.getFormVersionId(),
                application.getFormVersionNumber(),
                application.getStatus(),
                application.getAnswers(),
                application.getSubmittedAt(),
                application.getVersion()
        );
    }
}