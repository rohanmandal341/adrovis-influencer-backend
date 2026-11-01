package com.adrovis.influencer.dto;

import lombok.Data;

@Data
public class CampaignFilterRequest {
    private String campaignType;
    private Integer maxFollowers;
    private String brand;
    private String category;
    private String location;
    private Double minPayout;
    private Integer page = 0;
    private Integer size = 12;
    private String sortBy = "created_at";
    private String sortDirection = "DESC";
}
