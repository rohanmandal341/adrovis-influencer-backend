package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import java.util.List;

public record PublisherApplicationDetailResponse(
        PublisherCampaignApplicationResponse application,
        ApplicationFormVersionResponse form,
        List<ApplicationDecisionResponse> reviewHistory
) {
}