package com.adrovis.influencer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class UpdateAdminCampaignRequest {
    @NotBlank(message = "Brand name is required")
    private String brandName;

    @NotBlank(message = "Campaign title is required")
    private String campaignTitle;

    @NotBlank(message = "Campaign type is required")
    private String campaignType; // PAID, BARTER, HYBRID

    private String description;
    private String brandInstagramUrl;

    @NotBlank(message = "Application form URL is required")
    private String applicationFormUrl;

    @NotNull(message = "Images list is required")
    private List<String> images;

    // Compensation fields (synced into canonical_data.compensation)
    private String creatorPayout;
    private Double payoutAmountInr;
    private String productProvided;
    private String productValue;
    private Double productValueInr;
    private String compensationDetails;
    private Object payoutTiers;

    // Eligibility & Details (synced into canonical_data)
    private Integer minimumFollowers;
    private String minimumFollowersDisplay;
    private String gender;
    private List<String> locations;
    private List<String> otherRequirements;
    private List<String> deliverables;
    private List<String> categories;
    private String adRights;
    private String productName;
    private List<String> referenceLinks;

    // Schedule
    private String applicationDeadline;
    private String contentSubmissionDeadline;
    private String goLiveDate;
    private OffsetDateTime expiresAt;

    private List<String> importantRequirements;

    // Optional: save & approve in a single request
    private Boolean approveNow;
}