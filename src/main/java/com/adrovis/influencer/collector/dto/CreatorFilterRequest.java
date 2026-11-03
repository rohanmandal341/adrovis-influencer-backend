package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorFilterRequest {
    private String search;
    private String niche;
    private String location;
    private UUID addedBy;
    private String followerRange; // e.g. "0-10k", "10k-50k", "50k-100k", "100k+"
    private String timeRange;     // "TODAY", "LAST_7_DAYS", "THIS_MONTH", "ALL"
    private int page = 0;
    private int size = 10;
    private String sortBy = "createdAt";
    private String sortDir = "desc";
}