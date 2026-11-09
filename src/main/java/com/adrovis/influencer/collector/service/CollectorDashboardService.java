package com.adrovis.influencer.collector.service;

import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.collector.dto.*;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorCreatorRepository;
import com.adrovis.influencer.collector.repository.CollectorSubmissionRepository;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CollectorDashboardService {

    private final CollectorCreatorRepository creatorRepo;
    private final CollectorSubmissionRepository submissionRepo;
    private final CollectorTeamMemberRepository memberRepo;

    private static final DateTimeFormatter UI_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public DashboardOverviewDto getOverview(String timeRange) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = determineStartDate(timeRange, now);
        OffsetDateTime priorStartDate = startDate.minusDays(7);

        // 1. Core KPIs
        long totalCreators = creatorRepo.countTotalCreators();
        long uniqueEmails = creatorRepo.countUniqueEmails();
        long activeMembers = memberRepo.countByActiveTrue();
        long todayTargetProgress = submissionRepo.countTodayTeamUniqueLeads();
        long todayTargetGoal = activeMembers * 50;
        double todayPercentage = todayTargetGoal > 0 ? (todayTargetProgress * 100.0 / todayTargetGoal) : 0.0;

        // Growth metrics
        long recentCreated = creatorRepo.countCreatedSince(startDate);
        long priorCreated = creatorRepo.countCreatedBetween(priorStartDate, startDate);
        String creatorsGrowth = calculateGrowth(recentCreated, priorCreated);

        long recentEmails = creatorRepo.countEmailsCreatedSince(startDate);
        long priorEmails = creatorRepo.countEmailsCreatedBetween(priorStartDate, startDate);
        String emailsGrowth = calculateGrowth(recentEmails, priorEmails);

        DashboardOverviewDto.KpiMetrics kpis = DashboardOverviewDto.KpiMetrics.builder()
                .totalCreators(totalCreators)
                .totalCreatorsGrowth(creatorsGrowth)
                .uniqueEmails(uniqueEmails)
                .uniqueEmailsGrowth(emailsGrowth)
                .activeMembers(activeMembers)
                .todayTargetProgress(todayTargetProgress)
                .todayTargetGoal(todayTargetGoal > 0 ? todayTargetGoal : 50)
                .todayTargetPercentage(Math.round(todayPercentage * 10.0) / 10.0)
                .build();

        // 2. Team Goal
        DashboardOverviewDto.TeamGoal teamGoal = DashboardOverviewDto.TeamGoal.builder()
                .current(recentCreated)
                .target(2000)
                .percentage(Math.min(100.0, Math.round((recentCreated * 100.0 / 2000.0) * 10.0) / 10.0))
                .build();

        // 3. Team Competition Leaderboard
        List<Map<String, Object>> rawLeaderboard = submissionRepo.findTeamLeaderboard(startDate);
        List<TeamMemberRankDto> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rawLeaderboard) {
            String name = (String) row.get("memberName");
            String username = (String) row.get("tgUsername");
            Number cntNum = (Number) row.get("creatorsCount");
            long cnt = cntNum != null ? cntNum.longValue() : 0L;
            UUID memberId = (UUID) row.get("memberId");

            leaderboard.add(TeamMemberRankDto.builder()
                    .rank(rank++)
                    .memberId(memberId)
                    .name(name != null ? name : "Member")
                    .username(username != null ? "@" + username : null)
                    .initials(getInitials(name))
                    .creators(cnt)
                    .progress(Math.round((cnt * 100.0 / 350.0) * 10.0) / 10.0) // against weekly 350 target
                    .build());
        }

        // 4. Collection Trend
        List<Map<String, Object>> trendRows = creatorRepo.findDailyCollectionTrend(startDate);
        List<TrendDataPointDto> trend = trendRows.stream().map(row -> TrendDataPointDto.builder()
                .date((String) row.get("dayLabel"))
                .totalCreators(((Number) row.get("totalCreators")).longValue())
                .uniqueEmails(((Number) row.get("uniqueEmails")).longValue())
                .build()).collect(Collectors.toList());

        // 5. Niche Distribution
        List<Map<String, Object>> nicheRows = creatorRepo.findNicheDistribution();
        long nicheTotal = nicheRows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        List<NicheDistDto> nicheDist = nicheRows.stream().map(r -> {
            long count = ((Number) r.get("cnt")).longValue();
            double pct = nicheTotal > 0 ? Math.round((count * 100.0 / nicheTotal) * 10.0) / 10.0 : 0.0;
            return NicheDistDto.builder()
                    .niche((String) r.get("nicheName"))
                    .count(count)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());

        // 6. Location Insights
        List<Map<String, Object>> locRows = creatorRepo.findLocationDistribution();
        long locTotal = locRows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        List<LocationInsightDto> locDist = locRows.stream().map(r -> {
            long count = ((Number) r.get("cnt")).longValue();
            double pct = locTotal > 0 ? Math.round((count * 100.0 / locTotal) * 10.0) / 10.0 : 0.0;
            return LocationInsightDto.builder()
                    .location((String) r.get("locName"))
                    .count(count)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());

        // 7. Recent Creators
        Map<UUID, String> memberNameMap = memberRepo.findAll().stream()
                .collect(Collectors.toMap(CollectorTeamMember::getId, CollectorTeamMember::getDisplayName, (a, b) -> a));

        List<RecentCreatorDto> recent = creatorRepo.findTop5ByOrderByCreatedAtDesc().stream().map(c -> {
            String addedByName = c.getFirstAddedBy() != null ? memberNameMap.getOrDefault(c.getFirstAddedBy(), "Admin") : "Admin";
            return RecentCreatorDto.builder()
                    .id(c.getId())
                    .username(c.getInstagramUsername() != null ? "@" + c.getInstagramUsername() : "—")
                    .name(c.getFullName() != null ? c.getFullName() : "UNKNOWN")
                    .email(c.getEmail())
                    .followers(c.getFollowers())
                    .posts(c.getPostCount())
                    .niche(c.getNiche() != null ? c.getNiche().split(",")[0].trim() : "—")
                    .location(c.getLocation())
                    .addedBy(addedByName)
                    .addedByInitials(getInitials(addedByName))
                    .addedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(UI_DATE_FMT) : "—")
                    .build();
        }).collect(Collectors.toList());

        return DashboardOverviewDto.builder()
                .kpis(kpis)
                .teamGoal(teamGoal)
                .teamCompetition(leaderboard)
                .collectionTrend(trend)
                .nicheDistribution(nicheDist)
                .locationInsights(locDist)
                .recentCreators(recent)
                .build();
    }

    private OffsetDateTime determineStartDate(String timeRange, OffsetDateTime now) {
        if ("TODAY".equalsIgnoreCase(timeRange)) {
            return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        } else if ("THIS_MONTH".equalsIgnoreCase(timeRange)) {
            return now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(now.getOffset());
        }
        return now.minusDays(7); // default: LAST_7_DAYS
    }

    private String calculateGrowth(long current, long prior) {
        if (prior == 0) return current > 0 ? "+100%" : "0.0%";
        double growth = ((double) (current - prior) / prior) * 100.0;
        return (growth >= 0 ? "+" : "") + String.format(Locale.US, "%.1f%%", growth);
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "TM";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}