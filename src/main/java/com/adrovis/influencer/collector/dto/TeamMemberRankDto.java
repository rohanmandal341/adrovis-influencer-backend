package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMemberRankDto {
    private int rank;
    private UUID memberId;
    private String name;
    private String username;
    private String initials;
    private long creators;
    private double progress; // percentage relative to team quota
}