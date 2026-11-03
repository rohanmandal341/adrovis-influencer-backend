package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewDto {
    private KpiMetrics kpis;
    private TeamGoal teamGoal;
    private List<TeamMemberRankDto> teamCompetition;
    private List<TrendDataPointDto> collectionTrend;
    private List<NicheDistDto> nicheDistribution;
    private List<LocationInsightDto> locationInsights;
    private List<RecentCreatorDto> recentCreators;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KpiMetrics {
        private long totalCreators;
        private String totalCreatorsGrowth; // e.g. "+12.8%"
        private long uniqueEmails;
        private String uniqueEmailsGrowth;   // e.g. "+11.4%"
        private long activeMembers;
        private long todayTargetProgress;    // e.g. 32
        private long todayTargetGoal;        // e.g. 50
        private double todayTargetPercentage; // e.g. 64.0
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamGoal {
        private long current;
        private long target;
        private double percentage;
    }
}