package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface AnalyticsRepository extends JpaRepository<AnalyticsSessionEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO analytics_daily_visitors (visitor_hash, visit_date)
            VALUES (:visitorHash, CURRENT_DATE)
            ON CONFLICT (visitor_hash, visit_date) DO NOTHING
            """, nativeQuery = true)
    void recordDailyUniqueVisitor(@Param("visitorHash") String visitorHash);

    @Modifying
    @Query(value = """
            UPDATE analytics_sessions
            SET page_views = page_views + 1, last_active_at = NOW()
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    void incrementSessionPageViews(@Param("sessionId") UUID sessionId);

    @Modifying
    @Query(value = """
            UPDATE analytics_sessions
            SET duration_seconds = GREATEST(duration_seconds, :duration),
                active_dwell_seconds = GREATEST(active_dwell_seconds, :active),
                last_active_at = NOW()
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    void updateHeartbeat(@Param("sessionId") UUID sessionId,
                         @Param("duration") int duration,
                         @Param("active") int active);

    @Modifying
    @Query(value = """
            UPDATE analytics_sessions
            SET applied = TRUE
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    void markApplicationConverted(@Param("sessionId") UUID sessionId);

    @Query(value = "SELECT COUNT(*) FROM analytics_sessions WHERE last_active_at >= :since",
            nativeQuery = true)
    long countLiveActiveUsers(@Param("since") OffsetDateTime since);

    @Query(value = """
            SELECT COUNT(DISTINCT campaign_id) FROM analytics_sessions
            WHERE last_active_at >= :since AND campaign_id IS NOT NULL
            """, nativeQuery = true)
    long countLiveViewingCampaigns(@Param("since") OffsetDateTime since);

    @Query(value = "SELECT COUNT(*) FROM analytics_daily_visitors WHERE visit_date = CURRENT_DATE",
            nativeQuery = true)
    long countTodayUniqueVisitors();

    @Query(value = """
            SELECT COUNT(DISTINCT visitor_hash) FROM analytics_daily_visitors
            WHERE visit_date >= date_trunc('month', CURRENT_DATE)
            """, nativeQuery = true)
    long countThisMonthUniqueVisitors();

    @Query(value = "SELECT COUNT(DISTINCT visitor_hash) FROM analytics_daily_visitors",
            nativeQuery = true)
    long countTotalAllTimeUniqueVisitors();

    @Query(value = """
            SELECT COALESCE(AVG(duration_seconds), 0)       AS avg_duration,
                   COALESCE(AVG(active_dwell_seconds), 0)   AS avg_active,
                   COALESCE(100.0 * SUM(CASE WHEN page_views <= 1 THEN 1 ELSE 0 END)
                            / NULLIF(COUNT(*), 0), 0)       AS bounce_rate,
                   COALESCE(AVG(page_views), 1)             AS avg_pages
            FROM analytics_sessions
            """, nativeQuery = true)
    Map<String, Object> getOverallEngagementMetrics();

    @Query(value = """
            SELECT COALESCE(utm_source, 'direct') AS source, COUNT(*) AS sessions
            FROM analytics_sessions
            GROUP BY COALESCE(utm_source, 'direct')
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopTrafficSources();

    @Query(value = """
            SELECT campaign_id AS campaignId, COUNT(*) AS sessions
            FROM analytics_sessions
            WHERE campaign_id IS NOT NULL
            GROUP BY campaign_id
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopCampaignAttribution();

    @Query(value = """
            SELECT country, region, city, COUNT(*) AS sessions
            FROM analytics_sessions
            GROUP BY country, region, city
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopGeographicLocations();

    @Query(value = """
            SELECT COALESCE(100.0 * SUM(CASE WHEN device_type = 'Mobile' THEN 1 ELSE 0 END)
                            / NULLIF(COUNT(*), 0), 0)           AS mobile_pct,
                   COALESCE(100.0 * SUM(CASE WHEN is_in_app_browser THEN 1 ELSE 0 END)
                            / NULLIF(COUNT(*), 0), 0)           AS in_app_pct,
                   COALESCE(SUM(CASE WHEN applied THEN 1 ELSE 0 END), 0) AS total_applies,
                   COALESCE(SUM(CASE WHEN campaign_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS total_campaign_views
            FROM analytics_sessions
            """, nativeQuery = true)
    Map<String, Object> getEnvironmentAndConversionMetrics();
}