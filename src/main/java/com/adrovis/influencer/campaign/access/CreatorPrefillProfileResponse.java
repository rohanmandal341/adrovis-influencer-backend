package com.adrovis.influencer.campaign.access;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPrefillProfileResponse {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String gender;
    private String city;
    private String state;
    private String instagramProfileUrl;
    private Long followersCount;
    private String followersUpdatedAt;
    private String shippingAddress;
    private String pincode;
    private String youtubeUrl;
    private String portfolioUrl;
    private Integer age;
    private String profileType;
    private String preferredLanguage;
    private List<String> niches;
    private Map<String, String> customFields;
}