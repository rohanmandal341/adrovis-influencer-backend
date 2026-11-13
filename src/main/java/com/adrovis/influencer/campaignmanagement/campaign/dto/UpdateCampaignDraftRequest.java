package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCampaignDraftRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 10000)
        String description,

        @NotNull
        CollaborationType collaborationType
) {

    public UpdateCampaignDraftRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}