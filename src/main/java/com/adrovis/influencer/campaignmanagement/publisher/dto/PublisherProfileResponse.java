package com.adrovis.influencer.campaignmanagement.publisher.dto;

import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;

public record PublisherProfileResponse(
        UUID id,
        PublisherType type,
        String displayName,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static PublisherProfileResponse from(PublisherProfile profile) {
        return new PublisherProfileResponse(
                profile.getId(),
                profile.getPublisherType(),
                profile.getDisplayName(),
                profile.getVersion(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}