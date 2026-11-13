package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCampaignApplicationRequest(

        @NotNull
        CampaignApplicationStatus status,

        @Size(max = 3000)
        String internalNote,

        @Size(max = 2000)
        String creatorMessage
) {

    public ReviewCampaignApplicationRequest {
        internalNote = optional(internalNote);
        creatorMessage = optional(creatorMessage);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}