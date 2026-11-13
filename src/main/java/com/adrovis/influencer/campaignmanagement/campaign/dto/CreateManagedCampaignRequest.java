package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateManagedCampaignRequest(

        @NotNull(message = "Publisher profile is required.")
        UUID publisherProfileId,

        @NotBlank(message = "Campaign title is required.")
        @Size(
                max = 200,
                message = "Campaign title must not exceed 200 characters."
        )
        String title,

        @Size(
                max = 10000,
                message = "Description must not exceed 10000 characters."
        )
        String description,

        @NotNull(message = "Collaboration type is required.")
        CollaborationType collaborationType
) {

    public CreateManagedCampaignRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}