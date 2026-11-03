package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentCreatorDto {
    private UUID id;
    private String username;
    private String name;
    private String email;
    private Long followers;
    private Long posts;
    private String niche;
    private String location;
    private String addedBy;
    private String addedByInitials;
    private String addedAt;
}