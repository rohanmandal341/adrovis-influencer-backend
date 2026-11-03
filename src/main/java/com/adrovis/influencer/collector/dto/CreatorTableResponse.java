package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorTableResponse {
    private CreatorStatsBar stats;
    private List<RecentCreatorDto> items;
    private int currentPage;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreatorStatsBar {
        private long totalCreators;
        private String totalGrowth;
        private long withEmailCount;
        private double withEmailPercentage;
        private long withUsernameCount;
        private double withUsernamePercentage;
        private long withLocationCount;
        private double withLocationPercentage;
        private int totalNiches;
    }
}