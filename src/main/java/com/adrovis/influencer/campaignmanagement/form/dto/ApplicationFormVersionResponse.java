package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import com.adrovis.influencer.campaignmanagement.form.validation.ApplicationConsentNotice;

public record ApplicationFormVersionResponse(
        UUID id,
        UUID campaignId,
        int version,
        JsonNode definition,
        Instant createdAt,
        String consentNoticeVersion,
        String consentNoticeText

) {

    public static ApplicationFormVersionResponse from(
            CampaignApplicationFormVersion entity
    ) {
        return new ApplicationFormVersionResponse(
                entity.getId(),
                entity.getCampaign().getId(),
                entity.getVersionNumber(),
                entity.getDefinition(),
                entity.getCreatedAt(),
                ApplicationConsentNotice.VERSION,
                ApplicationConsentNotice.TEXT
        );
    }
}