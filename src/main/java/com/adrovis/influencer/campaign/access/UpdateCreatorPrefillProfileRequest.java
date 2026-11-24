package com.adrovis.influencer.campaign.access;

import lombok.Data;
import java.util.List;

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
    private List<String> niches;
}