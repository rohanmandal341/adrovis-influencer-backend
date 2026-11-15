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

@Repository
public interface AnalyticsRepository
        extends JpaRepository<AnalyticsSessionEntity, UUID>, JpaSpecificationExecutor<AnalyticsSessionEntity> {

    // =========================================================================
    // 1. INGESTION & ATOMIC UPSERTS
    // =========================================================================

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_daily_visitors (id, visitor_hash, visit_date, first_seen_at)
        SELECT gen_random_uuid(), :visitorHash, (NOW() AT TIME ZONE 'Asia/Kolkata')::date, NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM public.analytics_daily_visitors d
            WHERE d.visitor_hash = :visitorHash
              AND d.visit_date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
        )
        """, nativeQuery = true)
    void recordDailyUniqueVisitor(@Param("visitorHash") String visitorHash);

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_sessions (
            session_id, visitor_hash, person_id, entry_path, last_path,
            referrer, referrer_domain, utm_source, utm_medium, utm_campaign,
            utm_content, utm_term, campaign_id, channel, country, region, city,
            device_type, browser, os, in_app_browser, in_app_source,
            screen_resolution, timezone, language, returning_visitor,
            page_views, duration_seconds, active_dwell_seconds, applied,
            created_at, last_active_at
        )
        VALUES (
            :sessionId, :visitorHash, :visitorHash, :path, :path,
            :referrer, :referrerDomain, :utmSource, :utmMedium, :utmCampaign,
            :utmContent, :utmTerm, CAST(:campaignId AS UUID), :channel, :country, :region, :city,
            :deviceType, :browser, :os, :inAppBrowser, :inAppSource,
            :screenResolution, :timezone, :language,
            EXISTS (SELECT 1 FROM public.analytics_sessions s WHERE s.visitor_hash = :visitorHash AND s.session_id <> :sessionId),
            1, 0, 0, FALSE, NOW(), NOW()
        )
        ON CONFLICT (session_id) DO UPDATE SET
            last_path = EXCLUDED.last_path,
            page_views = public.analytics_sessions.page_views + 1,
            engaged = TRUE,
            campaign_id = COALESCE(EXCLUDED.campaign_id, public.analytics_sessions.campaign_id),
            country = CASE WHEN public.analytics_sessions.country IS NULL OR public.analytics_sessions.country = 'XX' THEN EXCLUDED.country ELSE public.analytics_sessions.country END,
            region = CASE WHEN public.analytics_sessions.region IS NULL OR public.analytics_sessions.region = 'Unknown' THEN EXCLUDED.region ELSE public.analytics_sessions.region END,
            city = CASE WHEN public.analytics_sessions.city IS NULL OR public.analytics_sessions.city = 'Unknown' THEN EXCLUDED.city ELSE public.analytics_sessions.city END,
            last_active_at = NOW()
        """, nativeQuery = true)
    void upsertSession(
            @Param("sessionId") UUID sessionId,
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
            @Param("inAppBrowser") boolean inAppBrowser,
            @Param("inAppSource") String inAppSource,
            @Param("screenResolution") String screenResolution,
            @Param("timezone") String timezone,
            @Param("language") String language
    );

    @Modifying
    @Query(value = """
        UPDATE public.analytics_sessions
        SET duration_seconds = GREATEST(duration_seconds, :durationSeconds),
            active_dwell_seconds = GREATEST(active_dwell_seconds, :activeDwellSeconds),
            engaged = (engaged OR :activeDwellSeconds >= 15 OR page_views >= 2 OR applied),
            last_active_at = NOW()
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    void updateHeartbeat(
            @Param("sessionId") UUID sessionId,
            @Param("durationSeconds") int durationSeconds,
            @Param("activeDwellSeconds") int activeDwellSeconds
    );

    @Modifying
    @Query(value = """
        UPDATE public.analytics_sessions
        SET applied = TRUE,
            engaged = TRUE,
            applied_at = COALESCE(applied_at, NOW()),
            last_active_at = NOW()
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    void markApplicationConverted(@Param("sessionId") UUID sessionId);

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_leads (
            session_id, visitor_hash, email, full_name, phone, user_type, consent_text, created_at
        )
        SELECT s.session_id, s.visitor_hash, :email, :fullName, :phone, :userType, :consentText, NOW()
        FROM public.analytics_sessions s
        WHERE s.session_id = :sessionId
        ON CONFLICT (session_id) DO UPDATE SET
            email = EXCLUDED.email,
            full_name = COALESCE(EXCLUDED.full_name, public.analytics_leads.full_name),
            phone = COALESCE(EXCLUDED.phone, public.analytics_leads.phone),
            user_type = COALESCE(EXCLUDED.user_type, public.analytics_leads.user_type)
        """, nativeQuery = true)
    int upsertLead(
            @Param("sessionId") UUID sessionId,
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("phone") String phone,
            @Param("userType") String userType,
            @Param("consentText") String consentText
    );

    // =========================================================================
    // 2. REAL-TIME & UNIQUE PERSON COUNTERS
    // =========================================================================

    /** Counts distinct real people active in the last 5 minutes, not raw sessions. */
    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE last_active_at >= NOW() - INTERVAL '5 minutes'
        """, nativeQuery = true)
    long countLiveActiveUsers();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE last_active_at >= NOW() - INTERVAL '5 minutes'
          AND (last_path LIKE '/campaigns%' OR campaign_id IS NOT NULL)
        """, nativeQuery = true)
    long countLiveViewingCampaigns();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
        """, nativeQuery = true)
    long countTodayUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date >= (NOW() AT TIME ZONE 'Asia/Kolkata')::date - 6
        """, nativeQuery = true)
    long countThisWeekUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE date_trunc('month', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', NOW() AT TIME ZONE 'Asia/Kolkata')
        """, nativeQuery = true)
    long countThisMonthUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        """, nativeQuery = true)
    long countTotalAllTimeUniqueVisitors();

    // =========================================================================
    // 3. OVERVIEW AGGREGATES & ENGAGEMENT
    // =========================================================================

    @Query(value = """
        SELECT
            COUNT(*) AS total_sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
            COUNT(*) FILTER (WHERE applied = TRUE) AS applied_sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FILTER (WHERE applied = TRUE) AS applied_people,
            COALESCE(AVG(duration_seconds), 0) AS avg_duration,
            COALESCE(AVG(active_dwell_seconds), 0) AS avg_active,
            COALESCE(AVG(page_views), 0) AS avg_pages,
            COALESCE(AVG(max_scroll_depth), 0) AS avg_scroll_depth,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE NOT (active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE)) / NULLIF(COUNT(*), 0), 0) AS bounce_rate,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE) / NULLIF(COUNT(*), 0), 0) AS engaged_rate,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Mobile') / NULLIF(COUNT(*), 0), 0) AS mobile_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Desktop') / NULLIF(COUNT(*), 0), 0) AS desktop_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Tablet') / NULLIF(COUNT(*), 0), 0) AS tablet_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE in_app_browser = TRUE) / NULLIF(COUNT(*), 0), 0) AS in_app_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE returning_visitor = TRUE) / NULLIF(COUNT(*), 0), 0) AS returning_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE is_vpn_or_proxy = TRUE OR is_datacenter = TRUE OR timezone_mismatch = TRUE) / NULLIF(COUNT(*), 0), 0) AS vpn_pct
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        """, nativeQuery = true)
    Map<String, Object> getSessionSummary(@Param("days") int days);

    @Query(value = """
        SELECT COUNT(*)
        FROM public.analytics_leads
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        """, nativeQuery = true)
    long countLeadsSince(@Param("days") int days);

    @Query(value = """
        SELECT
            channel,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions,
            ROUND(100.0 * COUNT(*) FILTER (WHERE applied = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY channel
        ORDER BY unique_visitors DESC, sessions DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getTopTrafficChannels(@Param("days") int days);

    @Query(value = """
        SELECT
            CAST(s.campaign_id AS VARCHAR) AS campaign_id,
            COALESCE(mc.title, c.campaign_title, 'Campaign ' || SUBSTRING(CAST(s.campaign_id AS VARCHAR), 1, 8)) AS campaign_title,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(s.person_id, s.visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE s.applied = TRUE) AS conversions,
            ROUND(100.0 * COUNT(*) FILTER (WHERE s.applied = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
        FROM public.analytics_sessions s
        LEFT JOIN public.managed_campaigns mc ON mc.id = s.campaign_id
        LEFT JOIN public.campaigns c ON c.id = s.campaign_id
        WHERE s.campaign_id IS NOT NULL
          AND s.created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY s.campaign_id, mc.title, c.campaign_title
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopCampaigns(@Param("days") int days);

    @Query(value = """
        SELECT
            COALESCE(referrer_domain, 'Direct / None') AS referrer_domain,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY COALESCE(referrer_domain, 'Direct / None')
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopReferrers(@Param("days") int days);

    @Query(value = """
        SELECT
            entry_path AS path,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds,
            ROUND(AVG(max_scroll_depth), 0) AS avg_scroll_depth,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY entry_path
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopLandingPages(@Param("days") int days);

    @Query(value = """
        SELECT
            COALESCE(NULLIF(city, 'Unknown'), 'Unknown') AS city,
            COALESCE(NULLIF(region, 'Unknown'), 'Unknown') AS region,
            COALESCE(NULLIF(country, 'XX'), 'Unknown') AS country,
            AVG(latitude) AS lat,
            AVG(longitude) AS lon,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY COALESCE(NULLIF(city, 'Unknown'), 'Unknown'),
                 COALESCE(NULLIF(region, 'Unknown'), 'Unknown'),
                 COALESCE(NULLIF(country, 'XX'), 'Unknown')
        ORDER BY unique_visitors DESC
        LIMIT 25
        """, nativeQuery = true)
    List<Map<String, Object>> getTopCities(@Param("days") int days);

    @Query(value = """
        SELECT
            to_char((created_at AT TIME ZONE 'Asia/Kolkata')::date, 'YYYY-MM-DD') AS day,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) AS sessions,
            COUNT(*) FILTER (WHERE active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE) AS engaged_sessions,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY (created_at AT TIME ZONE 'Asia/Kolkata')::date
        ORDER BY (created_at AT TIME ZONE 'Asia/Kolkata')::date ASC
        """, nativeQuery = true)
    List<Map<String, Object>> getDailyVisitorTrend(@Param("days") int days);

    @Query(value = """
        SELECT
            l.session_id,
            COALESCE(s.person_id, l.visitor_hash) AS person_id,
            l.email,
            l.full_name,
            l.phone,
            l.user_type,
            l.consent_text,
            l.created_at,
            s.channel,
            s.utm_source,
            s.utm_campaign,
            s.city,
            s.region,
            s.country,
            s.device_type,
            s.browser,
            s.os
        FROM public.analytics_leads l
        LEFT JOIN public.analytics_sessions s ON s.session_id = l.session_id
        ORDER BY l.created_at DESC
        LIMIT 200
        """, nativeQuery = true)
    List<Map<String, Object>> getRecentLeads();
}