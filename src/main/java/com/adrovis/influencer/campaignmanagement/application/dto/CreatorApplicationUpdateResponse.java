package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.time.Instant;

public record CreatorApplicationUpdateResponse(
        CampaignApplicationStatus previousStatus,
        CampaignApplicationStatus newStatus,
        String message,
        Instant createdAt
) {

    public static CreatorApplicationUpdateResponse from(
            CampaignApplicationDecision decision
    ) {
        return new CreatorApplicationUpdateResponse(
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getCreatorMessage(),
                decision.getCreatedAt()
        );
    }
}