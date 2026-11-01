package com.adrovis.influencer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyResponse {
    private UUID id;
    private String agencyName;
    private String contactPhone;
    private String contactEmail;
    private String instagramHandle;
    private String notes;
    private Integer activeCampaignsCount;
    private OffsetDateTime createdAt;
}
