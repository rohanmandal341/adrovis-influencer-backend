package com.adrovis.influencer.campaignmanagement.dashboard.dto;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import java.util.Map;

public record CampaignDashboardOverview(
        long totalCampaigns,
        Map<CampaignStatus, Long> campaignsByStatus,
        long totalApplications,
        Map<CampaignApplicationStatus, Long> applicationsByStatus
) {
}