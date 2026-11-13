package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationDecisionResponse(
        UUID id,
        UUID actorAccountId,
        CampaignApplicationStatus previousStatus,
        CampaignApplicationStatus newStatus,
        String internalNote,
        String creatorMessage,
        Instant createdAt
) {

    public static ApplicationDecisionResponse from(
            CampaignApplicationDecision decision
    ) {
        return new ApplicationDecisionResponse(
                decision.getId(),
                decision.getActorAccountId(),
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getInternalNote(),
                decision.getCreatorMessage(),
                decision.getCreatedAt()
        );
    }
}