package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notes:
 *  - "Today" is always the calendar day in Asia/Kolkata, independent of the DB timezone.
 *  - No CAST(x AS type) with "::" is used, so Hibernate never confuses it with a named parameter.
 *  - campaignId is passed as String and cast in SQL so a null never binds as bytea.
 */
@Repository
public interface AnalyticsRepository
        extends JpaRepository<AnalyticsSessionEntity, UUID>, JpaSpecificationExecutor<AnalyticsSessionEntity> {

    // ------------------------------------------------------------------
    // WRITES
    // ------------------------------------------------------------------

    /** Idempotent: 50 refreshes in one day still produce exactly 1 row. Returns 1 if new today, 0 if already counted. */
    @Modifying
    @Query(value = """
            INSERT INTO analytics_daily_visitors (visitor_hash, visit_date)
            VALUES (:visitorHash, CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date))
            ON CONFLICT (visitor_hash, visit_date) DO NOTHING
            """, nativeQuery = true)
    int recordDailyUniqueVisitor(@Param("visitorHash") String visitorHash);

    /** Atomic insert-or-increment. Safe under concurrent requests for the same session. */
    @Modifying
    @Query(value = """
            INSERT INTO analytics_sessions (
                session_id, visitor_hash, entry_path, last_path, referrer, referrer_domain,
                utm_source, utm_medium, utm_campaign, utm_content, utm_term, campaign_id, channel,
                country, region, city, device_type, browser, os, in_app_browser, in_app_source,
                screen_resolution, timezone, language, returning_visitor)
            VALUES (
                :sessionId, :visitorHash, :path, :path, :referrer, :referrerDomain,
                :utmSource, :utmMedium, :utmCampaign, :utmContent, :utmTerm,
                CAST(:campaignId AS uuid), :channel,
                :country, :region, :city, :deviceType, :browser, :os, :inApp, :inAppSource,
                :screenResolution, :timezone, :language,
                EXISTS (SELECT 1 FROM analytics_daily_visitors v
                        WHERE v.visitor_hash = :visitorHash
                          AND v.visit_date < CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date)))
            ON CONFLICT (session_id) DO UPDATE SET
                page_views     = analytics_sessions.page_views + 1,
                last_path      = EXCLUDED.last_path,
                last_active_at = now()
            """, nativeQuery = true)
    int upsertSession(@Param("sessionId") UUID sessionId,
                      @Param("visitorHash") String visitorHash,
                      @Param("path") String path,
                      @Param("referrer") String referrer,
                      @Param("referrerDomain") String referrerDomain,
                      @Param("utmSource") String utmSource,
                      @Param("utmMedium") String utmMedium,
                      @Param("utmCampaign") String utmCampaign,
                      @Param("utmContent") String utmContent,
                      @Param("utmTerm") String utmTerm,
                      @Param("campaignId") String campaignId,
                      @Param("channel") String channel,
                      @Param("country") String country,
                      @Param("region") String region,
                      @Param("city") String city,
                      @Param("deviceType") String deviceType,
                      @Param("browser") String browser,
                      @Param("os") String os,
                      @Param("inApp") boolean inApp,
                      @Param("inAppSource") String inAppSource,
                      @Param("screenResolution") String screenResolution,
                      @Param("timezone") String timezone,
                      @Param("language") String language);

    /** Counters only ever move forward; active dwell can never exceed total duration. */
    @Modifying
    @Query(value = """
            UPDATE analytics_sessions
            SET duration_seconds     = GREATEST(duration_seconds, :duration),
                active_dwell_seconds = LEAST(GREATEST(active_dwell_seconds, :active),
                                             GREATEST(duration_seconds, :duration)),
                last_active_at       = now()
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    int updateHeartbeat(@Param("sessionId") UUID sessionId,
                        @Param("duration") int duration,
                        @Param("active") int active);

    @Modifying
    @Query(value = """
            UPDATE analytics_sessions
            SET applied        = TRUE,
                applied_at     = COALESCE(applied_at, now()),
                last_active_at = now()
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    int markApplicationConverted(@Param("sessionId") UUID sessionId);

    /**
     * Opt-in lead capture. Copies first-touch attribution from the session at insert time.
     * Returns 0 if the session does not exist.
     */
    @Modifying
    @Query(value = """
            INSERT INTO analytics_leads (
                email, full_name, phone, user_type, consent_marketing, consent_text,
                session_id, first_channel, first_utm_source, first_utm_medium,
                first_utm_campaign, first_utm_content, first_referrer, first_city)
            SELECT :email, :fullName, :phone, :userType, TRUE, :consentText,
                   s.session_id, s.channel, s.utm_source, s.utm_medium,
                   s.utm_campaign, s.utm_content, s.referrer, s.city
            FROM analytics_sessions s
            WHERE s.session_id = :sessionId
            ON CONFLICT (email) DO UPDATE SET
                full_name  = COALESCE(EXCLUDED.full_name, analytics_leads.full_name),
                phone      = COALESCE(EXCLUDED.phone, analytics_leads.phone),
                user_type  = COALESCE(EXCLUDED.user_type, analytics_leads.user_type),
                updated_at = now()
            """, nativeQuery = true)
    int upsertLead(@Param("sessionId") UUID sessionId,
                   @Param("email") String email,
                   @Param("fullName") String fullName,
                   @Param("phone") String phone,
                   @Param("userType") String userType,
                   @Param("consentText") String consentText);

    // ------------------------------------------------------------------
    // LIVE + TRAFFIC VOLUME
    // ------------------------------------------------------------------

    @Query(value = "SELECT COUNT(*) FROM analytics_sessions WHERE last_active_at >= now() - interval '5 minutes'",
            nativeQuery = true)
    long countLiveActiveUsers();

    @Query(value = """
            SELECT COUNT(DISTINCT campaign_id) FROM analytics_sessions
            WHERE last_active_at >= now() - interval '5 minutes' AND campaign_id IS NOT NULL
            """, nativeQuery = true)
    long countLiveViewingCampaigns();

    @Query(value = """
            SELECT COUNT(*) FROM analytics_daily_visitors
            WHERE visit_date = CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date)
            """, nativeQuery = true)
    long countTodayUniqueVisitors();

    @Query(value = """
            SELECT COUNT(DISTINCT visitor_hash) FROM analytics_daily_visitors
            WHERE visit_date >= CAST(date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata') AS date)
            """, nativeQuery = true)
    long countThisMonthUniqueVisitors();

    @Query(value = "SELECT COUNT(DISTINCT visitor_hash) FROM analytics_daily_visitors", nativeQuery = true)
    long countTotalAllTimeUniqueVisitors();

    @Query(value = """
            SELECT visit_date AS day, COUNT(*) AS visitors
            FROM analytics_daily_visitors
            WHERE visit_date >= CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date) - CAST(:days AS integer)
            GROUP BY visit_date
            ORDER BY visit_date
            """, nativeQuery = true)
    List<Map<String, Object>> getDailyVisitorTrend(@Param("days") int days);

    // ------------------------------------------------------------------
    // AGGREGATES OVER A TIME WINDOW (last :days days)
    // ------------------------------------------------------------------

    /** One row, always. Aggregates without GROUP BY return a row even on an empty table. */
    @Query(value = """
            SELECT COUNT(*)                                                     AS total_sessions,
                   COALESCE(AVG(duration_seconds), 0)                           AS avg_duration,
                   COALESCE(AVG(active_dwell_seconds), 0)                       AS avg_active,
                   COALESCE(AVG(page_views), 0)                                 AS avg_pages,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE duration_seconds < 10 AND page_views <= 1)
                            / NULLIF(COUNT(*), 0), 0)                           AS bounce_rate,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Mobile')
                            / NULLIF(COUNT(*), 0), 0)                           AS mobile_pct,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Desktop')
                            / NULLIF(COUNT(*), 0), 0)                           AS desktop_pct,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Tablet')
                            / NULLIF(COUNT(*), 0), 0)                           AS tablet_pct,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE in_app_browser)
                            / NULLIF(COUNT(*), 0), 0)                           AS in_app_pct,
                   COALESCE(100.0 * COUNT(*) FILTER (WHERE returning_visitor)
                            / NULLIF(COUNT(*), 0), 0)                           AS returning_pct,
                   COUNT(*) FILTER (WHERE applied)                              AS applied_sessions
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
            """, nativeQuery = true)
    Map<String, Object> getSessionSummary(@Param("days") int days);

    @Query(value = """
            SELECT channel,
                   COUNT(*)                                        AS sessions,
                   COUNT(DISTINCT visitor_hash)                    AS visitors,
                   COUNT(*) FILTER (WHERE applied)                 AS applies,
                   COALESCE(ROUND(100.0 * COUNT(*) FILTER (WHERE applied)
                                  / NULLIF(COUNT(*), 0), 1), 0)    AS conversion_rate,
                   COALESCE(ROUND(AVG(active_dwell_seconds)), 0)   AS avg_active_seconds
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
            GROUP BY channel
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopTrafficChannels(@Param("days") int days);

    @Query(value = """
            SELECT utm_campaign, utm_source, utm_medium,
                   COUNT(*)                                        AS sessions,
                   COUNT(*) FILTER (WHERE applied)                 AS applies,
                   COALESCE(ROUND(100.0 * COUNT(*) FILTER (WHERE applied)
                                  / NULLIF(COUNT(*), 0), 1), 0)    AS conversion_rate,
                   COALESCE(ROUND(AVG(active_dwell_seconds)), 0)   AS avg_active_seconds
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
              AND utm_campaign IS NOT NULL
            GROUP BY utm_campaign, utm_source, utm_medium
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopCampaigns(@Param("days") int days);

    @Query(value = """
            SELECT referrer_domain, COUNT(*) AS sessions,
                   COUNT(*) FILTER (WHERE applied) AS applies
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
              AND referrer_domain IS NOT NULL
            GROUP BY referrer_domain
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopReferrers(@Param("days") int days);

    @Query(value = """
            SELECT entry_path, COUNT(*) AS sessions,
                   COUNT(*) FILTER (WHERE applied) AS applies,
                   COALESCE(ROUND(AVG(active_dwell_seconds)), 0) AS avg_active_seconds
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
            GROUP BY entry_path
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopLandingPages(@Param("days") int days);

    @Query(value = """
            SELECT COALESCE(city, 'Unknown') AS city,
                   COALESCE(region, 'Unknown') AS region,
                   COALESCE(country, 'XX') AS country,
                   COUNT(*) AS sessions,
                   COUNT(*) FILTER (WHERE applied) AS applies
            FROM analytics_sessions
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
            GROUP BY city, region, country
            ORDER BY sessions DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> getTopCities(@Param("days") int days);

    @Query(value = """
            SELECT COUNT(*) FROM analytics_leads
            WHERE created_at >= now() - (CAST(:days AS integer) * interval '1 day')
            """, nativeQuery = true)
    long countLeadsSince(@Param("days") int days);
}