package com.adrovis.influencer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminCampaignResponse {
    private String approvalStatus;
    private Boolean isActive;
    private String applicationFormUrl;
    private PublicCampaignResponse campaign;
}