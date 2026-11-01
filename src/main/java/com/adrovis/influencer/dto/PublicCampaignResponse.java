package com.adrovis.influencer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCampaignResponse {
    private UUID id;
    private String brandName;
    private String brandInstagramUrl;
    private String campaignTitle;
    private String campaignType;
    private String applyProxyUrl;
    private List<String> images;
    private Map<String, Object> compensation;
    private List<String> deliverables;
    private String adRights;
    private List<String> categories;
    private String productName;
    private Map<String, Object> eligibility;
    private Map<String, Object> schedule;
    private List<String> importantRequirements;
    private List<String> referenceLinks;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private Long viewsCount;
    private Long clicksCount;
}
