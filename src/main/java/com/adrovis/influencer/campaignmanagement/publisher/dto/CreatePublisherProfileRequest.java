package com.adrovis.influencer.campaignmanagement.publisher.dto;

import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePublisherProfileRequest(

        @NotNull(message = "Publisher type is required.")
        PublisherType type,

        @NotBlank(message = "Display name is required.")
        @Size(
                max = 120,
                message = "Display name must not exceed 120 characters."
        )
        String displayName
) {

    public CreatePublisherProfileRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}