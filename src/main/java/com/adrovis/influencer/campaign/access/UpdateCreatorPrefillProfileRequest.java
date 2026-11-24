package com.adrovis.influencer.campaign.access;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateCreatorPrefillProfileRequest {
    private String phoneNumber;
    private String gender;
    private String city;
    private String state;
    private String instagramProfileUrl;
    private Long followersCount;
    private String shippingAddress;
    private String pincode;
    private String youtubeUrl;
    private String portfolioUrl;
    private Integer age;
    private String profileType;
    private String preferredLanguage;
    private List<String> niches;
    // Arbitrary dynamic fields learned from any form (e.g., {"mother_name": "...", "skin_type": "..."})
    private Map<String, Object> customFields;
}