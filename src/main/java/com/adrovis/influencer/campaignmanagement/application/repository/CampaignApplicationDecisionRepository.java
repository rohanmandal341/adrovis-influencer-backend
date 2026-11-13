package com.adrovis.influencer.campaignmanagement.application.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CampaignApplicationDecisionRepository
        extends Repository<CampaignApplicationDecision, UUID> {

    CampaignApplicationDecision save(CampaignApplicationDecision decision);

    List<CampaignApplicationDecision>
    findAllByApplicationIdOrderByCreatedAtAscIdAsc(UUID applicationId);
}