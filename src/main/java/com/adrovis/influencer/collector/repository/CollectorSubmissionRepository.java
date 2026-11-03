package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface CollectorSubmissionRepository extends JpaRepository<CollectorSubmission, UUID> {

    @Query("SELECT COUNT(s) FROM CollectorSubmission s WHERE s.status = 'new' AND s.createdAt >= :startDate")
    long countNewSubmissionsSince(@Param("startDate") OffsetDateTime startDate);

    // Team Competition Leaderboard
    @Query(value = """
        SELECT s.team_member_id AS memberId,
               m.display_name AS memberName,
               m.telegram_username AS tgUsername,
               COUNT(DISTINCT s.extracted_email) AS creatorsCount
        FROM creator_submissions s
        JOIN team_members m ON s.team_member_id = m.id
        WHERE s.status = 'new' AND s.created_at >= :startDate
        GROUP BY s.team_member_id, m.display_name, m.telegram_username
        ORDER BY creatorsCount DESC
    """, nativeQuery = true)
    List<Map<String, Object>> findTeamLeaderboard(@Param("startDate") OffsetDateTime startDate);

    // Today's total team unique leads
    @Query(value = """
        SELECT COUNT(DISTINCT extracted_email)
        FROM creator_submissions
        WHERE status = 'new' AND date(created_at) = date(now())
    """, nativeQuery = true)
    long countTodayTeamUniqueLeads();
}