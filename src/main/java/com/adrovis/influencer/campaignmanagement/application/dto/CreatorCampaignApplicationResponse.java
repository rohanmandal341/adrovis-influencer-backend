package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record CreatorCampaignApplicationResponse(
        UUID id,
        UUID campaignId,
        String campaignTitle,
        UUID formVersionId,
        int formVersion,
        CampaignApplicationStatus status,
        JsonNode answers,
        Instant submittedAt,
        Long version
) {

    public static CreatorCampaignApplicationResponse from(
            ManagedCampaignApplication application
    ) {
        return new CreatorCampaignApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getCampaignTitle(),
                application.getFormVersionId(),
                application.getFormVersionNumber(),
                application.getStatus(),
                application.getAnswers(),
                application.getSubmittedAt(),
                application.getVersion()
        );
    }
}