package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.service.CampaignService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignApplyAccessService {

    private final CampaignService campaigns;
    private final GuestApplyRepository guests;
    private final int limit;

    public CampaignApplyAccessService(CampaignService campaigns, GuestApplyRepository guests,
                                      @Value("${app.guest-apply.limit:3}") int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalStateException("Guest campaign allowance must be between 1 and 100");
        }
        this.campaigns = campaigns;
        this.guests = guests;
        this.limit = limit;
    }

    @Transactional("primaryTransactionManager")
    public String grant(UUID campaignId, String guestHash, boolean authenticated) {
        // Missing campaigns and invalid destinations must not consume a guest allowance.
        campaigns.getApplicationDestination(campaignId);
        if (!authenticated && !guests.allow(guestHash, campaignId, limit)) {
            throw new SignupRequiredException();
        }
        return campaigns.trackAndGetApplicationUrl(campaignId);
    }
}
