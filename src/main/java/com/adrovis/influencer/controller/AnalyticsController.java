package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import com.adrovis.influencer.repository.AnalyticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/analytics")
@Slf4j
public class AnalyticsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> SORTABLE = Set.of(
            "createdAt", "lastActiveAt", "durationSeconds", "activeDwellSeconds",
            "pageViews", "city", "channel", "maxScrollDepth", "rageClicks");
    private static final Map<String, String> DEVICES = Map.of(
            "mobile", "Mobile", "desktop", "Desktop", "tablet", "Tablet");

    private static final String[] ADJECTIVES = {
            "Swift", "Calm", "Bright", "Bold", "Clever", "Noble", "Keen", "Steady",
            "Vivid", "Agile", "Silent", "Cosmic", "Golden", "Silver", "Amber", "Crimson",
            "Emerald", "Royal", "Brisk", "Radiant", "Prime", "Lucid", "Valiant", "Stellar",
            "Gentle", "Fierce", "Rapid", "Serene", "Daring", "Loyal", "Sharp", "Witty"
    };
    private static final String[] ANIMALS = {
            "Falcon", "Tiger", "Phoenix", "Panther", "Eagle", "Wolf", "Dolphin", "Lynx",
            "Hawk", "Otter", "Fox", "Raven", "Lion", "Bear", "Heron", "Stag",
            "Leopard", "Condor", "Jaguar", "Osprey", "Cobra", "Mustang", "Orca", "Puma",
            "Griffin", "Cheetah", "Badger", "Crane", "Gazelle", "Marlin", "Owl", "Viper"
    };
    // NOTE: all signatures are lower-case because the user agent is lower-cased before matching.
    private static final List<String> BOT_SIGNATURES = List.of(
            "bot", "spider", "crawl", "slurp", "ahrefs", "semrush", "mj12bot", "dotbot",
            "bytespider", "claudebot", "gptbot", "ccbot", "headlesschrome", "phantomjs",
            "puppeteer", "playwright", "selenium", "webdriver", "curl/", "wget/",
            "python-requests", "python-urllib", "aiohttp", "httpx", "go-http-client",
            "postmanruntime", "insomnia", "uptimerobot", "pingdom", "statuscake",
            "lighthouse", "pagespeed", "gtmetrix", "facebookexternalhit", "facebot",
            "twitterbot", "linkedinbot", "discordbot", "telegrambot", "applebot"
    );
    // Short words like "tor", "pia", "aws" were removed: they matched normal ISP names (e.g. "...operator...").
    private static final List<String> DATACENTER_KEYWORDS = List.of(
            "amazon", "digitalocean", "hetzner", "ovh", "linode", "akamai",
            "google cloud", "google llc", "microsoft corporation", "azure", "choopa",
            "vultr", "m247", "datacamp", "cdn77", "leaseweb", "contabo", "scaleway",
            "hostinger", "godaddy", "cloudflare", "fastly", "zscaler", "nordvpn",
            "expressvpn", "proton", "surfshark", "mullvad", "cyberghost"
    );

    private final AnalyticsRepository analyticsRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Cache real GeoIP lookups by /24 subnet so free GeoIP APIs stay fast and under rate limits. */
    private final Map<String, GeoData> geoCache = new ConcurrentHashMap<>();

    @Value("${analytics.visitor-salt:change-me-in-production}")
    private String visitorSalt;

    @Value("${analytics.excluded-ips:}")
    private String excludedIpsConfig;

    @Value("${analytics.excluded-emails:}")
    private String excludedEmailsConfig;

    @Value("${analytics.telegram-bot-token:}")
    private String telegramBotToken;

    @Value("${analytics.telegram-chat-id:}")
    private String telegramChatId;

    public AnalyticsController(
            AnalyticsRepository analyticsRepository,
            @Qualifier("primaryDataSource") DataSource dataSource,
            ObjectMapper objectMapper) {
        this.analyticsRepository = analyticsRepository;
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .build();
    }

    /** Automatically ensures all analytics tables and columns exist without losing historical data. */
    @PostConstruct
    public void ensureAnalyticsSchema() {
        try {
            jdbc.execute("""
                ALTER TABLE public.analytics_sessions
                    ADD COLUMN IF NOT EXISTS person_id VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS account_id UUID,
                    ADD COLUMN IF NOT EXISTS user_email VARCHAR(254),
                    ADD COLUMN IF NOT EXISTS user_name VARCHAR(120),
                    ADD COLUMN IF NOT EXISTS user_type VARCHAR(32) DEFAULT 'GUEST',
                    ADD COLUMN IF NOT EXISTS friendly_name VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS display_label VARCHAR(160),
                    ADD COLUMN IF NOT EXISTS masked_ip VARCHAR(48),
                    ADD COLUMN IF NOT EXISTS network_group_id VARCHAR(32),
                    ADD COLUMN IF NOT EXISTS isp VARCHAR(128),
                    ADD COLUMN IF NOT EXISTS asn VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS is_vpn_or_proxy BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS timezone_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS location_confidence VARCHAR(24) DEFAULT 'UNKNOWN',
                    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(32),
                    ADD COLUMN IF NOT EXISTS max_scroll_depth INTEGER NOT NULL DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS rage_clicks INTEGER NOT NULL DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS engaged BOOLEAN NOT NULL DEFAULT FALSE
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_visitors (
                    person_id VARCHAR(64) PRIMARY KEY,
                    visitor_hashes TEXT NOT NULL DEFAULT '',
                    account_id UUID,
                    user_email VARCHAR(254),
                    user_name VARCHAR(120),
                    user_type VARCHAR(32) NOT NULL DEFAULT 'GUEST',
                    friendly_name VARCHAR(64) NOT NULL,
                    display_label VARCHAR(160),
                    first_channel VARCHAR(32),
                    first_utm_source VARCHAR(128),
                    first_referrer VARCHAR(512),
                    last_channel VARCHAR(32),
                    last_utm_source VARCHAR(128),
                    last_referrer VARCHAR(512),
                    source_path TEXT DEFAULT '',
                    country VARCHAR(8),
                    region VARCHAR(128),
                    city VARCHAR(128),
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    location_confidence VARCHAR(24) DEFAULT 'UNKNOWN',
                    masked_ip VARCHAR(48),
                    isp VARCHAR(128),
                    asn VARCHAR(64),
                    network_group_id VARCHAR(32),
                    is_vpn_or_proxy BOOLEAN NOT NULL DEFAULT FALSE,
                    is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,
                    timezone_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
                    device_fingerprint VARCHAR(32),
                    devices_used VARCHAR(128) DEFAULT '',
                    total_sessions INTEGER NOT NULL DEFAULT 1,
                    total_page_views INTEGER NOT NULL DEFAULT 1,
                    total_active_seconds INTEGER NOT NULL DEFAULT 0,
                    campaign_views_count INTEGER NOT NULL DEFAULT 0,
                    apply_clicks_count INTEGER NOT NULL DEFAULT 0,
                    rage_clicks_count INTEGER NOT NULL DEFAULT 0,
                    max_scroll_depth INTEGER NOT NULL DEFAULT 0,
                    lead_score INTEGER NOT NULL DEFAULT 0,
                    applied BOOLEAN NOT NULL DEFAULT FALSE,
                    first_applied_at TIMESTAMPTZ,
                    admin_notes TEXT,
                    admin_tags VARCHAR(256),
                    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    session_id UUID NOT NULL,
                    visitor_hash VARCHAR(64) NOT NULL,
                    person_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    path VARCHAR(512) NOT NULL,
                    previous_path VARCHAR(512),
                    campaign_id UUID,
                    dwell_seconds INTEGER DEFAULT 0,
                    scroll_depth INTEGER DEFAULT 0,
                    element_target VARCHAR(256),
                    search_query VARCHAR(256),
                    filter_summary VARCHAR(512),
                    metadata JSONB,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_campaign_spend (
                    campaign_id UUID PRIMARY KEY,
                    spend_inr NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    notes VARCHAR(256),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_daily_summary (
                    day_ist DATE PRIMARY KEY,
                    unique_people BIGINT NOT NULL DEFAULT 0,
                    total_sessions BIGINT NOT NULL DEFAULT 0,
                    engaged_sessions BIGINT NOT NULL DEFAULT 0,
                    total_page_views BIGINT NOT NULL DEFAULT 0,
                    apply_clicks BIGINT NOT NULL DEFAULT 0,
                    applications_submitted BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        } catch (Exception e) {
            log.warn("Analytics schema auto-check skipped or partial: {}", e.getMessage());
        }
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    @Data
    public static class TrackVisitRequest {
        @NotBlank(message = "visitorId is required")
        @Size(max = 100)
        private String visitorId;

        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @NotBlank(message = "path is required")
        @Size(max = 2048)
        private String path;

        @Size(max = 2048) private String previousPath;
        @Size(max = 2048) private String referrer;
        @Size(max = 256)  private String utmSource;
        @Size(max = 256)  private String utmMedium;
        @Size(max = 256)  private String utmCampaign;
        @Size(max = 256)  private String utmContent;
        @Size(max = 256)  private String utmTerm;
        private UUID campaignId;
        @Size(max = 32)   private String screenResolution;
        @Size(max = 64)   private String timezone;

        // Optional identity fields if user is logged in on the frontend
        private UUID accountId;
        @Size(max = 254)  private String email;
        @Size(max = 120)  private String fullName;
        @Size(max = 32)   private String userType;
        @Min(0) @Max(86400) private Integer previousPageDwellSeconds;
    }

    @Data
    public static class HeartbeatRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @Min(0) @Max(86_400)
        private int durationSeconds;

        @Min(0) @Max(86_400)
        private int activeDwellSeconds;

        @Size(max = 512)
        private String currentPath;

        @Min(0) @Max(100)
        private Integer maxScrollDepth;
    }

    @Data
    public static class ConversionRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        private UUID campaignId;
    }

    @Data
    public static class LeadRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @NotBlank(message = "email is required")
        @Email(message = "email is invalid")
        @Size(max = 254)
        private String email;

        @Size(max = 120)
        private String fullName;

        @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "phone is invalid")
        private String phone;

        @Pattern(regexp = "^(BRAND|INFLUENCER|CREATOR|AGENCY|OTHER)$", message = "userType is invalid")
        private String userType;

        @AssertTrue(message = "consent is required")
        private boolean consent;

        @Size(max = 512)
        private String consentText;
    }

    @Data
    public static class TrackEventRequest {
        @NotBlank @Size(max = 100)
        private String visitorId;

        @NotNull
        private UUID sessionId;

        @NotBlank @Size(max = 32)
        private String eventType; // CLICK, SCROLL, APPLY_CLICK, FORM_START, FORM_SUBMIT, SEARCH, FILTER, CAMPAIGN_VIEW, SHARE, RAGE_CLICK

        @NotBlank @Size(max = 512)
        private String path;

        @Size(max = 512) private String previousPath;
        private UUID campaignId;
        @Min(0) @Max(86400) private Integer dwellSeconds;
        @Min(0) @Max(100)   private Integer scrollDepth;
        @Size(max = 256)    private String elementTarget;
        @Size(max = 256)    private String searchQuery;
        @Size(max = 512)    private String filterSummary;
        private Map<String, Object> metadata;
    }

    @Data
    public static class IdentifyVisitorRequest {
        @NotBlank @Size(max = 100)
        private String visitorId;

        private UUID sessionId;
        private UUID accountId;

        @Email @Size(max = 254)
        private String email;

        @Size(max = 120)
        private String fullName;

        @Size(max = 32)
        private String userType; // CREATOR, BRAND, AGENCY
    }

    @Data
    public static class VisitorAdminMetaRequest {
        @Size(max = 2000)
        private String adminNotes;

        @Size(max = 256)
        private String adminTags;
    }

    @Data
    public static class CampaignSpendRequest {
        @NotNull @Min(0)
        private BigDecimal spendInr;

        @Size(max = 256)
        private String notes;
    }

    private record GeoData(
            String country,
            String region,
            String city,
            Double lat,
            Double lon,
            String isp,
            String asn,
            boolean vpnOrProxy,
            boolean datacenter,
            String ipTimezone,
            String confidence
    ) {}

    private record AuthIdentity(UUID accountId, String email, String fullName, String userType) {}

    // =====================================================================
    // PUBLIC INGESTION ENDPOINTS
    // =====================================================================

    @PostMapping("/track")
    @Transactional
    public ResponseEntity<ApiResponse<String>> trackVisit(
            @Valid @RequestBody TrackVisitRequest req,
            HttpServletRequest http) {

        String ua = http.getHeader("User-Agent");
        String rawIp = extractClientIp(http);

        if (shouldIgnoreTraffic(http, ua, rawIp, req.getEmail())) {
            return ResponseEntity.ok(ApiResponse.ok("Ignored (bot/internal)"));
        }

        // Hash ONLY visitorId (stable across WiFi & mobile data). IP is NEVER part of identity.
        String visitorHash = hashStableVisitor(req.getVisitorId());

        AuthIdentity auth = resolveIdentity(req.getAccountId(), req.getEmail(), req.getFullName(), req.getUserType());
        String personId = resolveCanonicalPersonId(visitorHash, auth);

        String path = cleanPath(req.getPath());
        String prevPath = req.getPreviousPath() != null ? cleanPath(req.getPreviousPath()) : null;
        String referrer = clip(req.getReferrer(), 1024);
        String referrerDomain = extractDomain(referrer);

        String deviceType = detectDeviceType(ua);
        String browser = detectBrowser(ua);
        String os = detectOs(ua);
        String inAppSource = detectInAppSource(ua, referrer, deviceType);

        String utmSource = clip(req.getUtmSource(), 128);
        String utmMedium = clip(req.getUtmMedium(), 128);
        String channel = classifyChannel(utmSource, utmMedium, referrerDomain, inAppSource);

        // Real location: CF headers -> free GeoIP backup -> never fake data
        String browserTz = clip(req.getTimezone(), 64);
        GeoData geo = resolveRealLocation(http, rawIp, browserTz);
        boolean tzMismatch = isTimezoneMismatch(browserTz, geo.country(), geo.ipTimezone());
        String confidence = tzMismatch && !"UNKNOWN".equals(geo.confidence()) ? "LOW_VPN" : geo.confidence();

        String maskedIp = maskIp(rawIp);
        String networkGroupId = hashIdentifier("net|" + extractSubnetPrefix(rawIp) + "|" + Objects.toString(geo.isp(), "")).substring(0, 16);
        String language = extractLanguage(http);
        String screenRes = clip(req.getScreenResolution(), 24);
        String fingerprint = hashIdentifier(deviceType + "|" + os + "|" + Objects.toString(screenRes, "") + "|" + Objects.toString(browserTz, "") + "|" + Objects.toString(language, "")).substring(0, 16);

        String friendlyName = generateFriendlyName(personId);
        String placeLabel = (geo.city() != null && !"Unknown".equalsIgnoreCase(geo.city()))
                ? geo.city()
                : (geo.country() != null && !"XX".equalsIgnoreCase(geo.country()) ? geo.country() : "Unknown Place");
        String displayLabel = clip(placeLabel + " · " + browser + " · " + os + " " + shortTagFor(personId), 160);

        // 1. Idempotent daily unique visitor
        analyticsRepository.recordDailyUniqueVisitor(personId);

        // 2. Atomic session upsert
        analyticsRepository.upsertSession(
                req.getSessionId(),
                visitorHash,
                path,
                referrer,
                referrerDomain,
                utmSource,
                utmMedium,
                clip(req.getUtmCampaign(), 128),
                clip(req.getUtmContent(), 128),
                clip(req.getUtmTerm(), 128),
                req.getCampaignId() != null ? req.getCampaignId().toString() : null,
                channel,
                clip(geo.country(), 8),
                clip(geo.region(), 128),
                clip(geo.city(), 128),
                deviceType,
                browser,
                os,
                inAppSource != null,
                inAppSource,
                screenRes,
                browserTz,
                language);

        // 3. Enrich session with identity / location / network columns
        jdbc.update("""
            UPDATE public.analytics_sessions
            SET person_id = ?,
                account_id = COALESCE(?, account_id),
                user_email = COALESCE(?, user_email),
                user_name = COALESCE(?, user_name),
                user_type = COALESCE(?, user_type, 'GUEST'),
                friendly_name = COALESCE(friendly_name, ?),
                display_label = ?,
                masked_ip = COALESCE(?, masked_ip),
                network_group_id = COALESCE(?, network_group_id),
                isp = COALESCE(?, isp),
                asn = COALESCE(?, asn),
                is_vpn_or_proxy = (is_vpn_or_proxy OR ?),
                is_datacenter = (is_datacenter OR ?),
                timezone_mismatch = (timezone_mismatch OR ?),
                location_confidence = CASE WHEN location_confidence IS NULL OR location_confidence = 'UNKNOWN' THEN ? ELSE location_confidence END,
                latitude = COALESCE(?, latitude),
                longitude = COALESCE(?, longitude),
                device_fingerprint = COALESCE(?, device_fingerprint)
            WHERE session_id = ?
            """,
                personId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                friendlyName, displayLabel, maskedIp, networkGroupId, geo.isp(), geo.asn(),
                geo.vpnOrProxy(), geo.datacenter(), tzMismatch, confidence,
                geo.lat(), geo.lon(), fingerprint, req.getSessionId());

        // 4. Update previous page dwell time if supplied on navigation
        if (req.getPreviousPageDwellSeconds() != null && req.getPreviousPageDwellSeconds() > 0 && prevPath != null) {
            jdbc.update("""
                UPDATE public.analytics_events
                SET dwell_seconds = GREATEST(COALESCE(dwell_seconds, 0), ?)
                WHERE id = (
                    SELECT id FROM public.analytics_events
                    WHERE session_id = ? AND path = ? AND event_type = 'PAGE_VIEW'
                    ORDER BY created_at DESC LIMIT 1
                )
                """, Math.min(req.getPreviousPageDwellSeconds(), 86400), req.getSessionId(), prevPath);
        }

        // 5. Log PAGE_VIEW (and CAMPAIGN_VIEW) in analytics_events
        insertEvent(req.getSessionId(), visitorHash, personId, "PAGE_VIEW", path, prevPath,
                req.getCampaignId(), 0, 0, null, null, null, null);

        if (req.getCampaignId() != null) {
            insertEvent(req.getSessionId(), visitorHash, personId, "CAMPAIGN_VIEW", path, prevPath,
                    req.getCampaignId(), 0, 0, null, null, null, null);
        }

        // 6. Upsert master visitor profile & recalculate lead score
        upsertVisitorProfile(personId, visitorHash, auth, friendlyName, displayLabel, channel,
                utmSource, referrer, geo, confidence, maskedIp, networkGroupId, tzMismatch,
                fingerprint, deviceType, req.getCampaignId() != null);

        return ResponseEntity.ok(ApiResponse.ok("Tracked"));
    }

    @PostMapping("/heartbeat")
    @Transactional
    public ResponseEntity<ApiResponse<String>> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
        analyticsRepository.updateHeartbeat(
                req.getSessionId(),
                Math.max(0, req.getDurationSeconds()),
                Math.max(0, req.getActiveDwellSeconds()));

        if (req.getMaxScrollDepth() != null && req.getMaxScrollDepth() > 0) {
            jdbc.update("""
                UPDATE public.analytics_sessions
                SET max_scroll_depth = GREATEST(max_scroll_depth, ?)
                WHERE session_id = ?
                """, Math.min(100, req.getMaxScrollDepth()), req.getSessionId());
        }

        // Sync active dwell time and last_seen_at to analytics_visitors
        jdbc.update("""
            UPDATE public.analytics_visitors v
            SET last_seen_at = NOW(),
                max_scroll_depth = GREATEST(v.max_scroll_depth, COALESCE(s.max_scroll_depth, 0)),
                total_active_seconds = COALESCE((
                    SELECT SUM(active_dwell_seconds) FROM public.analytics_sessions WHERE person_id = v.person_id
                ), v.total_active_seconds),
                lead_score = LEAST(100,
                    LEAST(25, v.total_page_views * 3) +
                    LEAST(25, COALESCE((SELECT SUM(active_dwell_seconds) FROM public.analytics_sessions WHERE person_id = v.person_id), 0) / 15) +
                    LEAST(20, (v.total_sessions - 1) * 10) +
                    LEAST(15, v.campaign_views_count * 5) +
                    (CASE WHEN v.apply_clicks_count > 0 THEN 15 ELSE 0 END) +
                    (CASE WHEN v.applied THEN 20 ELSE 0 END)
                )
            FROM public.analytics_sessions s
            WHERE s.session_id = ? AND v.person_id = s.person_id
            """, req.getSessionId());

        return ResponseEntity.ok(ApiResponse.ok("Alive"));
    }

    @PostMapping("/conversion")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordConversion(@Valid @RequestBody ConversionRequest req) {
        analyticsRepository.markApplicationConverted(req.getSessionId());

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT visitor_hash, COALESCE(person_id, visitor_hash) AS person_id,
                   COALESCE(last_path, entry_path) AS path, campaign_id
            FROM public.analytics_sessions WHERE session_id = ?
            """, req.getSessionId());

        for (Map<String, Object> row : rows) {
            String vHash = String.valueOf(row.get("visitor_hash"));
            String pId = String.valueOf(row.get("person_id"));
            String path = String.valueOf(row.get("path"));
            UUID cId = req.getCampaignId();
            if (cId == null) {
                Object raw = row.get("campaign_id");
                if (raw instanceof UUID u) {
                    cId = u;
                } else if (raw != null) {
                    cId = UUID.fromString(raw.toString());
                }
            }

            insertEvent(req.getSessionId(), vHash, pId, "APPLY_CLICK", path, null, cId, 0, 0, null, null, null, null);

            jdbc.update("""
                UPDATE public.analytics_visitors
                SET applied = TRUE,
                    apply_clicks_count = apply_clicks_count + 1,
                    first_applied_at = COALESCE(first_applied_at, NOW()),
                    last_seen_at = NOW(),
                    lead_score = LEAST(100, lead_score + 20)
                WHERE person_id = ?
                """, pId);
        }

        return ResponseEntity.ok(ApiResponse.ok("Conversion recorded"));
    }

    @PostMapping("/lead")
    @Transactional
    public ResponseEntity<ApiResponse<String>> captureLead(@Valid @RequestBody LeadRequest req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedType = req.getUserType() != null ? req.getUserType().trim().toUpperCase(Locale.ROOT) : "CREATOR";
        if ("INFLUENCER".equals(normalizedType)) normalizedType = "CREATOR";

        int rows = analyticsRepository.upsertLead(
                req.getSessionId(),
                normalizedEmail,
                clip(req.getFullName(), 120),
                clip(req.getPhone(), 20),
                normalizedType.matches("BRAND|OTHER") ? normalizedType : "INFLUENCER",
                clip(req.getConsentText(), 512));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session");
        }

        // Link the visitor's anonymous sessions to their real email/name
        List<Map<String, Object>> sessionRows = jdbc.queryForList(
                "SELECT visitor_hash, COALESCE(last_path, entry_path) AS path FROM public.analytics_sessions WHERE session_id = ?",
                req.getSessionId());
        for (Map<String, Object> row : sessionRows) {
            String vHash = String.valueOf(row.get("visitor_hash"));
            String path = String.valueOf(row.get("path"));
            AuthIdentity identity = new AuthIdentity(null, normalizedEmail, clip(req.getFullName(), 120), normalizedType);
            String canonicalPersonId = stitchIdentity(vHash, identity);
            insertEvent(req.getSessionId(), vHash, canonicalPersonId, "FORM_SUBMIT", path, null, null, 0, 0, "lead_form", null, null, null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Lead saved"));
    }

    /** Records granular journey actions (clicks, scroll depth, search, filter, rage clicks, shares). */
    @PostMapping("/event")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordEvent(
            @Valid @RequestBody TrackEventRequest req,
            HttpServletRequest http) {

        String ua = http.getHeader("User-Agent");
        String rawIp = extractClientIp(http);
        if (shouldIgnoreTraffic(http, ua, rawIp, null)) {
            return ResponseEntity.ok(ApiResponse.ok("Ignored"));
        }

        String visitorHash = hashStableVisitor(req.getVisitorId());
        String personId = resolveCanonicalPersonId(visitorHash, resolveIdentity(null, null, null, null));
        String eventType = req.getEventType().trim().toUpperCase(Locale.ROOT);
        String path = cleanPath(req.getPath());
        String prevPath = req.getPreviousPath() != null ? cleanPath(req.getPreviousPath()) : null;
        int scroll = req.getScrollDepth() != null ? Math.min(100, Math.max(0, req.getScrollDepth())) : 0;
        int dwell = req.getDwellSeconds() != null ? Math.min(86400, Math.max(0, req.getDwellSeconds())) : 0;

        String metaJson = null;
        if (req.getMetadata() != null && !req.getMetadata().isEmpty()) {
            try {
                metaJson = objectMapper.writeValueAsString(req.getMetadata());
            } catch (Exception ignored) {
                metaJson = null;
            }
        }

        insertEvent(req.getSessionId(), visitorHash, personId, eventType, path, prevPath,
                req.getCampaignId(), dwell, scroll,
                clip(req.getElementTarget(), 256),
                clip(req.getSearchQuery(), 256),
                clip(req.getFilterSummary(), 512),
                metaJson);

        if ("SCROLL".equals(eventType) && scroll > 0) {
            jdbc.update("UPDATE public.analytics_sessions SET max_scroll_depth = GREATEST(max_scroll_depth, ?), last_active_at = NOW() WHERE session_id = ?", scroll, req.getSessionId());
            jdbc.update("UPDATE public.analytics_visitors SET max_scroll_depth = GREATEST(max_scroll_depth, ?), last_seen_at = NOW() WHERE person_id = ?", scroll, personId);
        } else if ("RAGE_CLICK".equals(eventType)) {
            jdbc.update("UPDATE public.analytics_sessions SET rage_clicks = rage_clicks + 1, last_active_at = NOW() WHERE session_id = ?", req.getSessionId());
            jdbc.update("UPDATE public.analytics_visitors SET rage_clicks_count = rage_clicks_count + 1, last_seen_at = NOW() WHERE person_id = ?", personId);
        } else if ("APPLY_CLICK".equals(eventType) || "FORM_SUBMIT".equals(eventType)) {
            analyticsRepository.markApplicationConverted(req.getSessionId());
            boolean isSubmit = "FORM_SUBMIT".equals(eventType);
            jdbc.update("""
                UPDATE public.analytics_visitors
                SET apply_clicks_count = apply_clicks_count + 1,
                    applied = (applied OR ?),
                    first_applied_at = CASE WHEN ? THEN COALESCE(first_applied_at, NOW()) ELSE first_applied_at END,
                    lead_score = LEAST(100, lead_score + 15),
                    last_seen_at = NOW()
                WHERE person_id = ?
                """, isSubmit, isSubmit, personId);
        } else if ("CAMPAIGN_VIEW".equals(eventType)) {
            jdbc.update("UPDATE public.analytics_visitors SET campaign_views_count = campaign_views_count + 1, lead_score = LEAST(100, lead_score + 5), last_seen_at = NOW() WHERE person_id = ?", personId);
        }

        return ResponseEntity.ok(ApiResponse.ok("Event recorded"));
    }

    /** Links anonymous visitor history to a logged-in account and merges phone/laptop visits into one person. */
    @PostMapping("/identify")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> identifyVisitor(
            @Valid @RequestBody IdentifyVisitorRequest req) {

        String visitorHash = hashStableVisitor(req.getVisitorId());
        AuthIdentity auth = resolveIdentity(req.getAccountId(), req.getEmail(), req.getFullName(), req.getUserType());
        String canonicalPersonId = stitchIdentity(visitorHash, auth);

        if (req.getSessionId() != null) {
            insertEvent(req.getSessionId(), visitorHash, canonicalPersonId, "LOGIN", "/", null, null, 0, 0, null, null, null, null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", canonicalPersonId);
        body.put("status", "Identity linked and historical sessions merged");
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    // =====================================================================
    // ADMIN ENDPOINTS
    // =====================================================================

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean excludeVpn) {

        int window = Math.min(Math.max(days, 1), 365);

        long liveUsers = analyticsRepository.countLiveActiveUsers();
        long liveCampaigns = analyticsRepository.countLiveViewingCampaigns();
        long todayVisitors = analyticsRepository.countTodayUniqueVisitors();
        long weekVisitors = analyticsRepository.countThisWeekUniqueVisitors();
        long monthVisitors = analyticsRepository.countThisMonthUniqueVisitors();
        long allTimeVisitors = analyticsRepository.countTotalAllTimeUniqueVisitors();

        Map<String, Object> s = analyticsRepository.getSessionSummary(window);
        long totalSessions = (long) num(s, "total_sessions", 0);
        long uniquePeople = (long) num(s, "unique_people", 0);
        long appliedSessions = (long) num(s, "applied_sessions", 0);
        long appliedPeople = (long) num(s, "applied_people", 0);
        double avgDuration = num(s, "avg_duration", 0);
        double avgActive = num(s, "avg_active", 0);
        double avgPages = num(s, "avg_pages", 0);
        double avgScroll = num(s, "avg_scroll_depth", 0);
        double bounce = num(s, "bounce_rate", 0);
        double engagedRate = num(s, "engaged_rate", 0);
        double mobilePct = num(s, "mobile_pct", 0);
        double desktopPct = num(s, "desktop_pct", 0);
        double tabletPct = num(s, "tablet_pct", 0);
        double inAppPct = num(s, "in_app_pct", 0);
        double returningPct = num(s, "returning_pct", 0);
        double vpnPct = num(s, "vpn_pct", 0);

        long leads = analyticsRepository.countLeadsSince(window);
        double convRate = totalSessions > 0 ? round1(appliedSessions * 100.0 / totalSessions) : 0.0;
        double personConvRate = uniquePeople > 0 ? round1(appliedPeople * 100.0 / uniquePeople) : 0.0;
        double leadRate = totalSessions > 0 ? round1(leads * 100.0 / totalSessions) : 0.0;

        int durMin = (int) (avgDuration / 60);
        int durSec = (int) (avgDuration % 60);
        int activeMin = (int) (avgActive / 60);
        int activeSec = (int) (avgActive % 60);

        List<Map<String, Object>> topChannels = safe(analyticsRepository.getTopTrafficChannels(window));
        List<Map<String, Object>> topCities = safe(analyticsRepository.getTopCities(window));

        long todayReturning = queryLong("""
            SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
            FROM public.analytics_sessions
            WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
              AND returning_visitor = TRUE
            """);
        String topSourceToday = topChannels.isEmpty() ? "Direct" : String.valueOf(topChannels.get(0).get("channel"));
        String topCityNow = topCities.isEmpty() ? "India" : String.valueOf(topCities.get(0).get("city"));
        String plainEnglishSummary = String.format(Locale.ROOT,
                "%d unique %s today (%d came back). %d live right now. Most traffic arrives via %s from %s.",
                todayVisitors, todayVisitors == 1 ? "person" : "people", todayReturning, liveUsers, topSourceToday, topCityNow);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowDays", window);
        out.put("plainEnglishSummary", plainEnglishSummary);

        Map<String, Object> live = new LinkedHashMap<>();
        live.put("activeUsersRightNow", liveUsers);
        live.put("currentlyViewingCampaigns", liveCampaigns);
        live.put("activeVisitorsList", getLiveVisitorsNow());
        out.put("live", live);

        Map<String, Object> traffic = new LinkedHashMap<>();
        traffic.put("todayUniqueVisitors", todayVisitors);
        traffic.put("thisWeekUniqueVisitors", weekVisitors);
        traffic.put("thisMonthUniqueVisitors", monthVisitors);
        traffic.put("allTimeUniqueVisitors", allTimeVisitors);
        traffic.put("uniquePeopleInWindow", uniquePeople);
        traffic.put("sessionsInWindow", totalSessions);
        out.put("trafficVolume", traffic);

        Map<String, Object> engagement = new LinkedHashMap<>();
        engagement.put("averageSessionDurationSeconds", Math.round(avgDuration));
        engagement.put("averageEngagementDisplay", String.format(Locale.ROOT, "%dm %02ds", durMin, durSec));
        engagement.put("averageActiveDwellSeconds", Math.round(avgActive));
        engagement.put("averageActiveDwellDisplay", String.format(Locale.ROOT, "%dm %02ds", activeMin, activeSec));
        engagement.put("bounceRatePercentage", round1(bounce));
        engagement.put("engagedVisitPercentage", round1(engagedRate));
        engagement.put("averagePageViewsPerSession", round1(avgPages));
        engagement.put("averageScrollDepthPercentage", round1(avgScroll));
        out.put("engagement", engagement);

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("mobilePercentage", round1(mobilePct));
        env.put("desktopPercentage", round1(desktopPct));
        env.put("tabletPercentage", round1(tabletPct));
        env.put("inAppBrowserPercentage", round1(inAppPct));
        env.put("returningVisitorPercentage", round1(returningPct));
        env.put("newVisitorPercentage", totalSessions > 0 ? round1(100.0 - returningPct) : 0.0);
        env.put("vpnOrDatacenterPercentage", round1(vpnPct));
        out.put("environment", env);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("sessions", totalSessions);
        funnel.put("uniquePeople", uniquePeople);
        funnel.put("applyButtonClicks", appliedSessions);
        funnel.put("uniquePeopleApplied", appliedPeople);
        funnel.put("overallConversionRate", convRate);
        funnel.put("personConversionRate", personConvRate);
        funnel.put("optInLeads", leads);
        funnel.put("leadCaptureRate", leadRate);
        out.put("funnelConversion", funnel);

        out.put("topTrafficChannels", topChannels);
        out.put("topCampaigns", safe(analyticsRepository.getTopCampaigns(window)));
        out.put("topReferrers", safe(analyticsRepository.getTopReferrers(window)));
        out.put("topLandingPages", safe(analyticsRepository.getTopLandingPages(window)));
        out.put("topCities", topCities);
        out.put("dailyVisitorTrend", safe(analyticsRepository.getDailyVisitorTrend(window)));

        out.put("visitFrequencyGroups", getVisitFrequencyGroups(window));
        out.put("userTypeSplit", getUserTypeSplit(window));
        out.put("firstVsLastSource", getFirstVsLastAttribution(window));
        out.put("bestTimeToPostHeatmapIst", getIstHourlyHeatmap(window));
        out.put("timeToConvert", getTimeToConvertStats(window));
        out.put("hotVisitors", getHotVisitors(10));
        out.put("smartAlerts", checkTrafficAnomalies());

        Map<String, Object> definitions = new LinkedHashMap<>();
        definitions.put("activeUsersRightNow", "Distinct real people who opened a page or scrolled in the last 5 minutes.");
        definitions.put("uniquePeopleInWindow", "Every person counted once across mobile data, WiFi, and linked devices.");
        definitions.put("engagedVisitPercentage", "Visits where the person spent 15+ active seconds, viewed 2+ pages, or clicked Apply.");
        definitions.put("bounceRatePercentage", "Visits that left in under 15 active seconds after seeing only 1 page.");
        definitions.put("leadScore", "0-100 score based on pages viewed, active time, return visits, campaign views, and apply clicks.");
        definitions.put("locationConfidence", "How sure we are of the place: EXACT_CITY, STATE_ONLY, COUNTRY_ONLY, LOW_VPN, or UNKNOWN.");
        out.put("metricDefinitions", definitions);

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Visitors directory (one row per person). */
    @GetMapping("/visitors")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisitors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean applied,
            @RequestParam(required = false) Boolean hideVpn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "last_seen_at") String sortBy) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            where.append("""
                 AND (LOWER(COALESCE(user_name, '')) LIKE ?
                   OR LOWER(COALESCE(user_email, '')) LIKE ?
                   OR LOWER(COALESCE(friendly_name, '')) LIKE ?
                   OR LOWER(COALESCE(display_label, '')) LIKE ?
                   OR LOWER(COALESCE(city, '')) LIKE ?
                   OR LOWER(COALESCE(isp, '')) LIKE ?
                   OR LOWER(COALESCE(admin_notes, '')) LIKE ?
                   OR LOWER(COALESCE(admin_tags, '')) LIKE ?)
                """);
            for (int i = 0; i < 8; i++) params.add(like);
        }
        if (country != null && !country.isBlank()) {
            where.append(" AND UPPER(COALESCE(country, '')) = ? ");
            params.add(country.trim().toUpperCase(Locale.ROOT));
        }
        if (state != null && !state.isBlank()) {
            where.append(" AND LOWER(COALESCE(region, '')) = ? ");
            params.add(state.trim().toLowerCase(Locale.ROOT));
        }
        if (city != null && !city.isBlank()) {
            where.append(" AND LOWER(COALESCE(city, '')) = ? ");
            params.add(city.trim().toLowerCase(Locale.ROOT));
        }
        if (userType != null && !userType.isBlank()) {
            where.append(" AND UPPER(COALESCE(user_type, 'GUEST')) = ? ");
            params.add(userType.trim().toUpperCase(Locale.ROOT));
        }
        if (channel != null && !channel.isBlank()) {
            where.append(" AND (LOWER(COALESCE(first_channel, '')) = ? OR LOWER(COALESCE(last_channel, '')) = ?) ");
            params.add(channel.trim().toLowerCase(Locale.ROOT));
            params.add(channel.trim().toLowerCase(Locale.ROOT));
        }
        if (applied != null) {
            where.append(" AND applied = ? ");
            params.add(applied);
        }
        if (Boolean.TRUE.equals(hideVpn)) {
            where.append(" AND is_vpn_or_proxy = FALSE AND is_datacenter = FALSE ");
        }

        String orderCol = switch (sortBy) {
            case "lead_score", "leadScore" -> "lead_score DESC, last_seen_at DESC";
            case "total_sessions", "visits" -> "total_sessions DESC, last_seen_at DESC";
            case "total_active_seconds", "activeTime" -> "total_active_seconds DESC, last_seen_at DESC";
            case "first_seen_at", "firstSeen" -> "first_seen_at DESC";
            default -> "last_seen_at DESC";
        };

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM public.analytics_visitors " + where, Long.class, params.toArray());
        long totalElements = total != null ? total : 0L;

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT * FROM public.analytics_visitors " + where + " ORDER BY " + orderCol + " LIMIT ? OFFSET ?",
                queryParams.toArray());

        for (Map<String, Object> row : items) {
            row.put("primaryDisplayName", resolvePrimaryName(row));
            row.put("quickTags", buildQuickTags(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", items);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalElements", totalElements);
        result.put("totalPages", (int) Math.ceil((double) totalElements / safeSize));
        result.put("sameNetworkGroups", getSameNetworkGroups());
        result.put("possibleDuplicateHints", getPossibleDuplicateHints());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** One-person full story timeline + cross-device sessions. */
    @GetMapping("/visitors/{personId}/timeline")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisitorTimeline(
            @PathVariable String personId) {

        List<Map<String, Object>> profiles = jdbc.queryForList(
                "SELECT * FROM public.analytics_visitors WHERE person_id = ?", personId);
        Map<String, Object> profile = profiles.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(profiles.get(0));
        if (!profile.isEmpty()) {
            profile.put("primaryDisplayName", resolvePrimaryName(profile));
            profile.put("quickTags", buildQuickTags(profile));
        }

        List<Map<String, Object>> sessions = jdbc.queryForList("""
            SELECT session_id, visitor_hash, device_type, browser, os, in_app_browser, in_app_source,
                   channel, utm_source, utm_campaign, referrer_domain, entry_path, last_path,
                   city, region, country, location_confidence, masked_ip, isp,
                   is_vpn_or_proxy, is_datacenter, timezone_mismatch,
                   page_views, duration_seconds, active_dwell_seconds, max_scroll_depth,
                   rage_clicks, engaged, applied, applied_at, created_at, last_active_at
            FROM public.analytics_sessions
            WHERE person_id = ? OR visitor_hash = ?
            ORDER BY created_at DESC
            """, personId, personId);

        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT id, session_id, event_type, path, previous_path, campaign_id,
                   dwell_seconds, scroll_depth, element_target, search_query, filter_summary,
                   metadata, created_at
            FROM public.analytics_events
            WHERE person_id = ? OR visitor_hash = ?
            ORDER BY created_at ASC
            LIMIT 500
            """, personId, personId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("visitor", profile);
        out.put("sessions", sessions);
        out.put("timelineEvents", events);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Save admin notes and labels on a visitor. */
    @PatchMapping("/visitors/{personId}/meta")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> updateVisitorAdminMeta(
            @PathVariable String personId,
            @Valid @RequestBody VisitorAdminMetaRequest req) {

        int updated = jdbc.update("""
            UPDATE public.analytics_visitors
            SET admin_notes = ?,
                admin_tags = ?
            WHERE person_id = ?
            """, clip(req.getAdminNotes(), 2000), clip(req.getAdminTags(), 256), personId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visitor not found");
        }
        return ResponseEntity.ok(ApiResponse.ok("Visitor notes and tags saved"));
    }

    /** Privacy data-delete option for any person. */
    @DeleteMapping("/visitors/{personId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteVisitorData(@PathVariable String personId) {
        jdbc.update("DELETE FROM public.analytics_leads WHERE visitor_hash = ? OR session_id IN (SELECT session_id FROM public.analytics_sessions WHERE person_id = ?)", personId, personId);
        jdbc.update("DELETE FROM public.analytics_events WHERE person_id = ? OR visitor_hash = ?", personId, personId);
        jdbc.update("DELETE FROM public.analytics_sessions WHERE person_id = ? OR visitor_hash = ?", personId, personId);
        jdbc.update("DELETE FROM public.analytics_visitors WHERE person_id = ?", personId);
        return ResponseEntity.ok(ApiResponse.ok("All analytics data for this person has been permanently deleted"));
    }

    /** Journeys, top paths, drop-off report, before/after page flow, scroll depth, rage clicks, search/filter insights. */
    @GetMapping("/journeys")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJourneys(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String inspectPath) {

        int window = Math.min(Math.max(days, 1), 365);
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("pageAttentionAndScroll", jdbc.queryForList("""
            SELECT
                path,
                COUNT(*) FILTER (WHERE event_type = 'PAGE_VIEW') AS views,
                COUNT(DISTINCT person_id) AS unique_people,
                ROUND(AVG(NULLIF(dwell_seconds, 0)) FILTER (WHERE event_type = 'PAGE_VIEW'), 1) AS avg_dwell_seconds,
                ROUND(AVG(NULLIF(scroll_depth, 0)) FILTER (WHERE event_type = 'SCROLL'), 0) AS avg_scroll_depth,
                COUNT(*) FILTER (WHERE event_type = 'SCROLL' AND scroll_depth >= 75) AS read_75_pct_count
            FROM public.analytics_events
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path
            ORDER BY views DESC
            LIMIT 25
            """, window));

        out.put("mostCommonPaths", jdbc.queryForList("""
            WITH session_routes AS (
                SELECT
                    e.session_id,
                    STRING_AGG(e.path, ' → ' ORDER BY e.created_at ASC) AS route,
                    BOOL_OR(s.applied) AS converted
                FROM (
                    SELECT session_id, path, created_at,
                           ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at ASC) AS step_no
                    FROM public.analytics_events
                    WHERE event_type = 'PAGE_VIEW'
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                ) e
                JOIN public.analytics_sessions s ON s.session_id = e.session_id
                WHERE e.step_no <= 5
                GROUP BY e.session_id
            )
            SELECT
                route,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE converted = TRUE) AS conversions,
                ROUND(100.0 * COUNT(*) FILTER (WHERE converted = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
            FROM session_routes
            GROUP BY route
            ORDER BY sessions DESC
            LIMIT 20
            """, window));

        out.put("pathFlowTransitions", jdbc.queryForList("""
            SELECT
                previous_path AS from_page,
                path AS to_page,
                COUNT(*) AS transitions
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW'
              AND previous_path IS NOT NULL
              AND previous_path <> path
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY previous_path, path
            ORDER BY transitions DESC
            LIMIT 30
            """, window));

        out.put("dropOffPages", jdbc.queryForList("""
            SELECT
                COALESCE(last_path, entry_path) AS exit_page,
                COUNT(*) AS total_exits,
                COUNT(*) FILTER (WHERE applied = FALSE) AS unconverted_exits,
                ROUND(100.0 * COUNT(*) FILTER (WHERE applied = FALSE) / NULLIF(COUNT(*), 0), 1) AS drop_off_rate_pct,
                ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds_before_exit
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(last_path, entry_path)
            ORDER BY unconverted_exits DESC
            LIMIT 20
            """, window));

        String targetPage = (inspectPath != null && !inspectPath.isBlank()) ? cleanPath(inspectPath) : "/campaigns";
        Map<String, Object> beforeAfter = new LinkedHashMap<>();
        beforeAfter.put("inspectedPath", targetPage);
        beforeAfter.put("viewedBefore", jdbc.queryForList("""
            SELECT previous_path AS page, COUNT(*) AS count
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW' AND path = ? AND previous_path IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY previous_path ORDER BY count DESC LIMIT 10
            """, targetPage, window));
        beforeAfter.put("viewedAfter", jdbc.queryForList("""
            SELECT path AS page, COUNT(*) AS count
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW' AND previous_path = ? AND path IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path ORDER BY count DESC LIMIT 10
            """, targetPage, window));
        out.put("pageBeforeAndAfter", beforeAfter);

        out.put("frustrationSignals", jdbc.queryForList("""
            SELECT
                path,
                COALESCE(element_target, 'Unknown element') AS element,
                COUNT(*) AS rage_click_events,
                COUNT(DISTINCT person_id) AS affected_people
            FROM public.analytics_events
            WHERE event_type = 'RAGE_CLICK'
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path, COALESCE(element_target, 'Unknown element')
            ORDER BY rage_click_events DESC
            LIMIT 20
            """, window));

        out.put("campaignFunnels", getCampaignLevelFunnels(window));

        out.put("topSearches", jdbc.queryForList("""
            SELECT LOWER(TRIM(search_query)) AS query, COUNT(*) AS searches, COUNT(DISTINCT person_id) AS unique_people
            FROM public.analytics_events
            WHERE event_type = 'SEARCH' AND search_query IS NOT NULL AND TRIM(search_query) <> ''
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY LOWER(TRIM(search_query))
            ORDER BY searches DESC LIMIT 20
            """, window));

        out.put("topFiltersUsed", jdbc.queryForList("""
            SELECT filter_summary AS filter_used, COUNT(*) AS uses, COUNT(DISTINCT person_id) AS unique_people
            FROM public.analytics_events
            WHERE event_type = 'FILTER' AND filter_summary IS NOT NULL AND TRIM(filter_summary) <> ''
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY filter_summary
            ORDER BY uses DESC LIMIT 20
            """, window));

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Custom funnel builder (any sequence of paths or action types). */
    @GetMapping("/funnel")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> evaluateFunnel(
            @RequestParam(defaultValue = "/,/campaigns,CAMPAIGN_VIEW,APPLY_CLICK,FORM_SUBMIT") String steps,
            @RequestParam(defaultValue = "30") int days) {

        int window = Math.min(Math.max(days, 1), 365);
        List<String> stepList = Arrays.stream(steps.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(8)
                .toList();

        Set<String> previousStepPeople = null;
        long firstStepCount = 0;
        List<Map<String, Object>> funnelOut = new ArrayList<>();

        for (int i = 0; i < stepList.size(); i++) {
            String step = stepList.get(i);
            List<String> matchingPeople;
            if ("APPLIED".equalsIgnoreCase(step)) {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT COALESCE(person_id, visitor_hash)
                    FROM public.analytics_sessions
                    WHERE applied = TRUE AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, window);
            } else if (step.startsWith("/")) {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT person_id FROM public.analytics_events
                    WHERE event_type = 'PAGE_VIEW' AND (path = ? OR path LIKE ?)
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, step, step + "%", window);
            } else {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT person_id FROM public.analytics_events
                    WHERE UPPER(event_type) = ?
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, step.toUpperCase(Locale.ROOT), window);
            }

            Set<String> currentSet = new HashSet<>(matchingPeople);
            if (previousStepPeople != null) {
                currentSet.retainAll(previousStepPeople);
            }
            long count = currentSet.size();
            long prevCount = previousStepPeople == null ? count : previousStepPeople.size();
            if (i == 0) firstStepCount = count;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stepNumber", i + 1);
            row.put("step", step);
            row.put("people", count);
            row.put("retainedFromPreviousPct", prevCount > 0 ? round1(count * 100.0 / prevCount) : 0.0);
            row.put("dropOffFromPrevious", Math.max(0, prevCount - count));
            row.put("dropOffFromPreviousPct", prevCount > 0 ? round1((prevCount - count) * 100.0 / prevCount) : 0.0);
            row.put("overallConversionFromStartPct", firstStepCount > 0 ? round1(count * 100.0 / firstStepCount) : 0.0);
            funnelOut.add(row);

            previousStepPeople = currentSet;
        }

        return ResponseEntity.ok(ApiResponse.ok(funnelOut));
    }

    /** World map dots, state -> city drill-down, ISP/network breakdown, VPN & confidence stats. */
    @GetMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLocationAnalytics(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "IN") String country,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean excludeVpn) {

        int window = Math.min(Math.max(days, 1), 365);
        String vpnFilter = excludeVpn ? " AND is_vpn_or_proxy = FALSE AND is_datacenter = FALSE " : "";
        String countryCode = country.trim().toUpperCase(Locale.ROOT);

        Map<String, Object> out = new LinkedHashMap<>();

        String cityDotsSql = """
            SELECT
                city, region AS state, country,
                ROUND(CAST(AVG(latitude) AS NUMERIC), 4) AS lat,
                ROUND(CAST(AVG(longitude) AS NUMERIC), 4) AS lon,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE latitude IS NOT NULL AND longitude IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """ + vpnFilter + """
            GROUP BY city, region, country
            ORDER BY unique_people DESC
            LIMIT 100
            """;
        out.put("cityMapDots", jdbc.queryForList(cityDotsSql, window));

        String statesSql = """
            SELECT
                COALESCE(NULLIF(region, 'Unknown'), 'Unknown State') AS state,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE UPPER(COALESCE(country, 'IN')) = ?
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """ + vpnFilter + """
            GROUP BY COALESCE(NULLIF(region, 'Unknown'), 'Unknown State')
            ORDER BY unique_people DESC
            """;
        out.put("statesBreakdown", jdbc.queryForList(statesSql, countryCode, window));

        if (state != null && !state.isBlank()) {
            String citiesSql = """
                SELECT
                    COALESCE(NULLIF(city, 'Unknown'), 'Unknown City') AS city,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                    COUNT(*) AS sessions,
                    COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
                FROM public.analytics_sessions
                WHERE UPPER(COALESCE(country, 'IN')) = ?
                  AND LOWER(COALESCE(region, '')) = ?
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                """ + vpnFilter + """
                GROUP BY COALESCE(NULLIF(city, 'Unknown'), 'Unknown City')
                ORDER BY unique_people DESC
                """;
            out.put("citiesInSelectedState", jdbc.queryForList(citiesSql, countryCode, state.trim().toLowerCase(Locale.ROOT), window));
        }

        out.put("topIspsAndNetworks", jdbc.queryForList("""
            SELECT
                COALESCE(isp, 'Unknown Provider') AS isp,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE device_type = 'Mobile') AS mobile_sessions,
                COUNT(*) FILTER (WHERE device_type = 'Desktop') AS desktop_sessions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(isp, 'Unknown Provider')
            ORDER BY unique_people DESC
            LIMIT 20
            """, window));

        out.put("locationConfidenceSummary", jdbc.queryForList("""
            SELECT
                COALESCE(location_confidence, 'UNKNOWN') AS confidence,
                COUNT(*) AS sessions,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(location_confidence, 'UNKNOWN')
            ORDER BY sessions DESC
            """, window));

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Set campaign spend so the dashboard calculates Cost Per Apply (CPA). */
    @PutMapping("/campaigns/{campaignId}/spend")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> updateCampaignSpend(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignSpendRequest req) {

        jdbc.update("""
            INSERT INTO public.analytics_campaign_spend (campaign_id, spend_inr, notes, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (campaign_id) DO UPDATE SET
                spend_inr = EXCLUDED.spend_inr,
                notes = EXCLUDED.notes,
                updated_at = NOW()
            """, campaignId, req.getSpendInr(), clip(req.getNotes(), 256));

        return ResponseEntity.ok(ApiResponse.ok("Campaign spend saved"));
    }

    /** Export Visitors, Sessions, Journeys, or Leads as CSV. */
    @GetMapping("/export/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String type,
            @RequestParam(defaultValue = "30") int days) {

        int window = Math.min(Math.max(days, 1), 365);
        StringBuilder csv = new StringBuilder("\uFEFF");

        switch (type.toLowerCase(Locale.ROOT)) {
            case "visitors" -> {
                csv.append("Person ID,Name,Email,User Type,City,State,Country,Confidence,ISP,Masked IP,First Source,Last Source,Source Path,Visits,Page Views,Active Seconds,Lead Score,Applied,Admin Tags,Admin Notes,First Seen,Last Seen\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM public.analytics_visitors ORDER BY last_seen_at DESC LIMIT 5000");
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("person_id"), resolvePrimaryName(r), r.get("user_email"), r.get("user_type"),
                            r.get("city"), r.get("region"), r.get("country"), r.get("location_confidence"),
                            r.get("isp"), r.get("masked_ip"), r.get("first_channel"), r.get("last_channel"),
                            r.get("source_path"), r.get("total_sessions"), r.get("total_page_views"),
                            r.get("total_active_seconds"), r.get("lead_score"), r.get("applied"),
                            r.get("admin_tags"), r.get("admin_notes"), r.get("first_seen_at"), r.get("last_seen_at")
                    ));
                }
            }
            case "journeys" -> {
                csv.append("Event Time,Person ID,Session ID,Event Type,Path,Previous Path,Dwell Seconds,Scroll Depth,Element Target,Search Query,Filter Summary\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT created_at, person_id, session_id, event_type, path, previous_path, dwell_seconds, scroll_depth, element_target, search_query, filter_summary
                    FROM public.analytics_events
                    WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    ORDER BY created_at DESC LIMIT 5000
                    """, window);
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("created_at"), r.get("person_id"), r.get("session_id"), r.get("event_type"),
                            r.get("path"), r.get("previous_path"), r.get("dwell_seconds"), r.get("scroll_depth"),
                            r.get("element_target"), r.get("search_query"), r.get("filter_summary")
                    ));
                }
            }
            case "leads" -> {
                csv.append("Created At,Email,Full Name,Phone,User Type,Channel,UTM Source,City,State,Country,Device\r\n");
                for (Map<String, Object> r : safe(analyticsRepository.getRecentLeads())) {
                    csv.append(csvLine(
                            r.get("created_at"), r.get("email"), r.get("full_name"), r.get("phone"),
                            r.get("user_type"), r.get("channel"), r.get("utm_source"), r.get("city"),
                            r.get("region"), r.get("country"), r.get("device_type")
                    ));
                }
            }
            default -> {
                csv.append("Session ID,Person ID,Display Label,Channel,Entry Path,Last Path,City,State,Country,ISP,Masked IP,Device,Browser,OS,Page Views,Active Seconds,Scroll Depth,Engaged,Applied,Created At\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT session_id, person_id, display_label, channel, entry_path, last_path, city, region, country, isp, masked_ip,
                           device_type, browser, os, page_views, active_dwell_seconds, max_scroll_depth, engaged, applied, created_at
                    FROM public.analytics_sessions
                    WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    ORDER BY created_at DESC LIMIT 5000
                    """, window);
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("session_id"), r.get("person_id"), r.get("display_label"), r.get("channel"),
                            r.get("entry_path"), r.get("last_path"), r.get("city"), r.get("region"), r.get("country"),
                            r.get("isp"), r.get("masked_ip"), r.get("device_type"), r.get("browser"), r.get("os"),
                            r.get("page_views"), r.get("active_dwell_seconds"), r.get("max_scroll_depth"),
                            r.get("engaged"), r.get("applied"), r.get("created_at")
                    ));
                }
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("brandcollabz-" + type + "-export.csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<AnalyticsSessionEntity>>> getSessions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean applied,
            @RequestParam(required = false) Boolean excludeVpn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean activeNow,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        final String device;
        if (deviceType == null || deviceType.isBlank()) {
            device = null;
        } else {
            device = DEVICES.get(deviceType.trim().toLowerCase(Locale.ROOT));
            if (device == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "deviceType must be Mobile, Desktop or Tablet");
            }
        }

        final String search = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);
        final String channelFilter = (channel == null || channel.isBlank()) ? null : channel.trim();

        Specification<AnalyticsSessionEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            if (search != null) {
                String like = "%" + escapeLike(search) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("city")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("region")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("friendlyName")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("displayLabel")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("userName")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("userEmail")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("isp")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("entryPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("lastPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("referrer")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmSource")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmMedium")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmCampaign")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmContent")), like, '\\')));
            }
            if (device != null) {
                p.add(cb.equal(root.get("deviceType"), device));
            }
            if (channelFilter != null) {
                p.add(cb.equal(cb.lower(root.<String>get("channel")), channelFilter.toLowerCase(Locale.ROOT)));
            }
            if (country != null && !country.isBlank()) {
                p.add(cb.equal(cb.upper(root.<String>get("country")), country.trim().toUpperCase(Locale.ROOT)));
            }
            if (city != null && !city.isBlank()) {
                p.add(cb.equal(cb.lower(root.<String>get("city")), city.trim().toLowerCase(Locale.ROOT)));
            }
            if (applied != null) {
                p.add(cb.equal(root.get("applied"), applied));
            }
            if (Boolean.TRUE.equals(excludeVpn)) {
                p.add(cb.isFalse(root.<Boolean>get("vpnOrProxy")));
                p.add(cb.isFalse(root.<Boolean>get("datacenter")));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"),
                        from.atStartOfDay(IST).toOffsetDateTime()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.<OffsetDateTime>get("createdAt"),
                        to.plusDays(1).atStartOfDay(IST).toOffsetDateTime()));
            }
            if (Boolean.TRUE.equals(activeNow)) {
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("lastActiveAt"),
                        OffsetDateTime.now().minusMinutes(5)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        String sortField = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortField).and(Sort.by(Sort.Direction.DESC, "sessionId"));

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        Page<AnalyticsSessionEntity> result = analyticsRepository.findAll(spec, pageable);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<AnalyticsSessionEntity>> getSession(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"))));
    }

    @GetMapping("/leads")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLeads() {
        return ResponseEntity.ok(ApiResponse.ok(safe(analyticsRepository.getRecentLeads())));
    }

    // =====================================================================
    // INTERNAL ANALYTICS ENGINES (IDENTITY, LOCATION, FUNNELS, SCORING)
    // =====================================================================

    /** Extracts identity from Spring Security JWT if logged in, or from explicit request parameters. */
    private AuthIdentity resolveIdentity(UUID reqAccountId, String reqEmail, String reqName, String reqType) {
        UUID accId = reqAccountId;
        String email = reqEmail != null && !reqEmail.isBlank() ? reqEmail.trim().toLowerCase(Locale.ROOT) : null;
        String name = clip(reqName, 120);
        String role = reqType != null && !reqType.isBlank() ? reqType.trim().toUpperCase(Locale.ROOT) : null;

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                if (accId == null && jwt.getSubject() != null) {
                    try {
                        accId = UUID.fromString(jwt.getSubject());
                    } catch (Exception ignored) {
                        // subject is not a UUID
                    }
                }
                if (email == null && jwt.hasClaim("email")) {
                    email = jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT);
                }
                if (name == null && jwt.hasClaim("name")) {
                    name = clip(jwt.getClaimAsString("name"), 120);
                }
                if (role == null && jwt.hasClaim("role")) {
                    role = jwt.getClaimAsString("role").toUpperCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            // anonymous request
        }

        if ("INFLUENCER".equals(role)) role = "CREATOR";
        if (role != null && !Set.of("CREATOR", "BRAND", "AGENCY", "ADMIN").contains(role)) {
            role = "GUEST";
        }
        return new AuthIdentity(accId, email, name, role);
    }

    private String resolveCanonicalPersonId(String visitorHash, AuthIdentity auth) {
        if (auth.accountId() != null || auth.email() != null) {
            return stitchIdentity(visitorHash, auth);
        }
        List<String> existing = jdbc.queryForList(
                "SELECT person_id FROM public.analytics_sessions WHERE visitor_hash = ? AND person_id IS NOT NULL ORDER BY last_active_at DESC LIMIT 1",
                String.class, visitorHash);
        return existing.isEmpty() ? visitorHash : existing.get(0);
    }

    /** Connects anonymous visits to real accounts and merges phone + laptop duplicates. */
    private String stitchIdentity(String visitorHash, AuthIdentity auth) {
        String canonicalId = visitorHash;
        if (auth.accountId() != null) {
            canonicalId = "acc_" + hashIdentifier(auth.accountId().toString()).substring(0, 48);
        } else if (auth.email() != null) {
            canonicalId = "usr_" + hashIdentifier(auth.email()).substring(0, 48);
        }

        jdbc.update("""
            UPDATE public.analytics_sessions
            SET person_id = ?,
                account_id = COALESCE(CAST(? AS UUID), account_id),
                user_email = COALESCE(CAST(? AS VARCHAR), user_email),
                user_name = COALESCE(CAST(? AS VARCHAR), user_name),
                user_type = COALESCE(CAST(? AS VARCHAR), user_type)
            WHERE visitor_hash = ?
               OR (CAST(? AS UUID) IS NOT NULL AND account_id = CAST(? AS UUID))
               OR (CAST(? AS VARCHAR) IS NOT NULL AND user_email = CAST(? AS VARCHAR))
            """,
                canonicalId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                visitorHash, auth.accountId(), auth.accountId(), auth.email(), auth.email());

        jdbc.update("UPDATE public.analytics_events SET person_id = ? WHERE visitor_hash = ?", canonicalId, visitorHash);

        // Merge any earlier anonymous visitor row into the canonical person_id
        if (!canonicalId.equals(visitorHash)) {
            jdbc.update("""
                UPDATE public.analytics_visitors
                SET person_id = ?,
                    account_id = COALESCE(CAST(? AS UUID), account_id),
                    user_email = COALESCE(CAST(? AS VARCHAR), user_email),
                    user_name = COALESCE(CAST(? AS VARCHAR), user_name),
                    user_type = COALESCE(CAST(? AS VARCHAR), user_type)
                WHERE person_id = ?
                  AND NOT EXISTS (SELECT 1 FROM public.analytics_visitors WHERE person_id = ?)
                """, canonicalId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(), visitorHash, canonicalId);

            jdbc.update("DELETE FROM public.analytics_visitors WHERE person_id = ?", visitorHash);
        }

        return canonicalId;
    }

    private void upsertVisitorProfile(
            String personId, String visitorHash, AuthIdentity auth,
            String friendlyName, String displayLabel, String channel,
            String utmSource, String referrer, GeoData geo, String confidence,
            String maskedIp, String networkGroupId, boolean tzMismatch,
            String fingerprint, String deviceType, boolean campaignViewed) {

        jdbc.update("""
            INSERT INTO public.analytics_visitors (
                person_id, visitor_hashes, account_id, user_email, user_name, user_type,
                friendly_name, display_label, first_channel, first_utm_source, first_referrer,
                last_channel, last_utm_source, last_referrer, source_path,
                country, region, city, latitude, longitude, location_confidence,
                masked_ip, isp, asn, network_group_id, is_vpn_or_proxy, is_datacenter,
                timezone_mismatch, device_fingerprint, devices_used,
                total_sessions, total_page_views, campaign_views_count, lead_score,
                first_seen_at, last_seen_at
            )
            VALUES (
                ?, ?, ?, ?, ?, COALESCE(?, 'GUEST'),
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?,
                1, 1, ?, ?,
                NOW(), NOW()
            )
            ON CONFLICT (person_id) DO UPDATE SET
                visitor_hashes = CASE
                    WHEN POSITION(? IN public.analytics_visitors.visitor_hashes) > 0 THEN public.analytics_visitors.visitor_hashes
                    ELSE LEFT(public.analytics_visitors.visitor_hashes || ',' || ?, 500)
                END,
                account_id = COALESCE(EXCLUDED.account_id, public.analytics_visitors.account_id),
                user_email = COALESCE(EXCLUDED.user_email, public.analytics_visitors.user_email),
                user_name = COALESCE(EXCLUDED.user_name, public.analytics_visitors.user_name),
                user_type = CASE WHEN EXCLUDED.user_type <> 'GUEST' THEN EXCLUDED.user_type ELSE public.analytics_visitors.user_type END,
                display_label = EXCLUDED.display_label,
                last_channel = EXCLUDED.last_channel,
                last_utm_source = COALESCE(EXCLUDED.last_utm_source, public.analytics_visitors.last_utm_source),
                last_referrer = COALESCE(EXCLUDED.last_referrer, public.analytics_visitors.last_referrer),
                source_path = CASE
                    WHEN public.analytics_visitors.source_path LIKE '%' || EXCLUDED.last_channel THEN public.analytics_visitors.source_path
                    ELSE LEFT(public.analytics_visitors.source_path || ' → ' || EXCLUDED.last_channel, 500)
                END,
                country = CASE WHEN EXCLUDED.country IS NOT NULL AND EXCLUDED.country <> 'XX' THEN EXCLUDED.country ELSE public.analytics_visitors.country END,
                region = CASE WHEN EXCLUDED.region IS NOT NULL AND EXCLUDED.region <> 'Unknown' THEN EXCLUDED.region ELSE public.analytics_visitors.region END,
                city = CASE WHEN EXCLUDED.city IS NOT NULL AND EXCLUDED.city <> 'Unknown' THEN EXCLUDED.city ELSE public.analytics_visitors.city END,
                latitude = COALESCE(EXCLUDED.latitude, public.analytics_visitors.latitude),
                longitude = COALESCE(EXCLUDED.longitude, public.analytics_visitors.longitude),
                location_confidence = CASE WHEN EXCLUDED.location_confidence <> 'UNKNOWN' THEN EXCLUDED.location_confidence ELSE public.analytics_visitors.location_confidence END,
                masked_ip = COALESCE(EXCLUDED.masked_ip, public.analytics_visitors.masked_ip),
                isp = COALESCE(EXCLUDED.isp, public.analytics_visitors.isp),
                asn = COALESCE(EXCLUDED.asn, public.analytics_visitors.asn),
                network_group_id = COALESCE(EXCLUDED.network_group_id, public.analytics_visitors.network_group_id),
                is_vpn_or_proxy = (public.analytics_visitors.is_vpn_or_proxy OR EXCLUDED.is_vpn_or_proxy),
                is_datacenter = (public.analytics_visitors.is_datacenter OR EXCLUDED.is_datacenter),
                timezone_mismatch = (public.analytics_visitors.timezone_mismatch OR EXCLUDED.timezone_mismatch),
                device_fingerprint = COALESCE(EXCLUDED.device_fingerprint, public.analytics_visitors.device_fingerprint),
                devices_used = CASE
                    WHEN POSITION(? IN COALESCE(public.analytics_visitors.devices_used, '')) > 0 THEN public.analytics_visitors.devices_used
                    ELSE LEFT(COALESCE(public.analytics_visitors.devices_used, '') || CASE WHEN COALESCE(public.analytics_visitors.devices_used, '') = '' THEN '' ELSE ',' END || ?, 128)
                END,
                total_sessions = (SELECT COUNT(*) FROM public.analytics_sessions WHERE person_id = ?),
                total_page_views = public.analytics_visitors.total_page_views + 1,
                campaign_views_count = public.analytics_visitors.campaign_views_count + (CASE WHEN ? THEN 1 ELSE 0 END),
                lead_score = LEAST(100,
                    LEAST(25, (public.analytics_visitors.total_page_views + 1) * 3) +
                    LEAST(25, public.analytics_visitors.total_active_seconds / 15) +
                    LEAST(20, GREATEST(0, (SELECT COUNT(*) FROM public.analytics_sessions WHERE person_id = ?) - 1) * 10) +
                    LEAST(15, (public.analytics_visitors.campaign_views_count + (CASE WHEN ? THEN 1 ELSE 0 END)) * 5) +
                    (CASE WHEN public.analytics_visitors.apply_clicks_count > 0 THEN 15 ELSE 0 END) +
                    (CASE WHEN public.analytics_visitors.applied THEN 20 ELSE 0 END)
                ),
                last_seen_at = NOW()
            """,
                personId, visitorHash, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                friendlyName, displayLabel, channel, utmSource, clip(referrer, 512),
                channel, utmSource, clip(referrer, 512), channel,
                clip(geo.country(), 8), clip(geo.region(), 128), clip(geo.city(), 128), geo.lat(), geo.lon(), confidence,
                maskedIp, geo.isp(), geo.asn(), networkGroupId, geo.vpnOrProxy(), geo.datacenter(),
                tzMismatch, fingerprint, deviceType,
                campaignViewed ? 1 : 0, campaignViewed ? 8 : 3,
                visitorHash, visitorHash,
                deviceType, deviceType,
                personId, campaignViewed, personId, campaignViewed);
    }

    private void insertEvent(
            UUID sessionId, String visitorHash, String personId, String eventType,
            String path, String previousPath, UUID campaignId, int dwellSeconds,
            int scrollDepth, String elementTarget, String searchQuery,
            String filterSummary, String metadataJson) {

        jdbc.update("""
            INSERT INTO public.analytics_events (
                session_id, visitor_hash, person_id, event_type, path, previous_path,
                campaign_id, dwell_seconds, scroll_depth, element_target,
                search_query, filter_summary, metadata, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), NOW())
            """,
                sessionId, visitorHash, personId, eventType, path, previousPath,
                campaignId, dwellSeconds, scrollDepth, elementTarget,
                searchQuery, filterSummary, metadataJson);
    }

    // =====================================================================
    // REAL LOCATION RESOLUTION (CF HEADERS + FREE GEOIP BACKUP)
    // =====================================================================

    private GeoData resolveRealLocation(HttpServletRequest req, String ip, String browserTz) {
        String cfCountry = headerOrNull(req, "CF-IPCountry", "X-Vercel-IP-Country", "X-Country-Code");
        String cfRegion = headerOrNull(req, "CF-Region", "X-Vercel-IP-Country-Region", "X-Region-Name");
        String cfCity = headerOrNull(req, "CF-IPCity", "X-Vercel-IP-City", "X-City-Name");
        Double cfLat = parseDoubleOrNull(headerOrNull(req, "CF-IPLatitude", "X-Vercel-IP-Latitude", null));
        Double cfLon = parseDoubleOrNull(headerOrNull(req, "CF-IPLongitude", "X-Vercel-IP-Longitude", null));

        boolean hasValidCfCity = cfCity != null && !"Unknown".equalsIgnoreCase(cfCity)
                && cfCountry != null && !"XX".equalsIgnoreCase(cfCountry);

        // Private/loopback IP (local dev) -> honest UNKNOWN, never fake data
        if (isPrivateOrLoopbackIp(ip)) {
            if (hasValidCfCity) {
                return new GeoData(cfCountry, cfRegion != null ? cfRegion : "Unknown", cfCity, cfLat, cfLon, null, null, false, false, null, "EXACT_CITY");
            }
            return new GeoData(
                    cfCountry != null ? cfCountry : "XX",
                    cfRegion != null ? cfRegion : "Unknown",
                    cfCity != null ? cfCity : "Unknown",
                    cfLat, cfLon, "Local Network", null, false, false, null, "UNKNOWN");
        }

        String subnetKey = extractSubnetPrefix(ip);
        GeoData cached = geoCache.get(subnetKey);
        if (cached != null) {
            return mergeHeaderWithGeo(cfCountry, cfRegion, cfCity, cfLat, cfLon, cached);
        }

        GeoData lookedUp = fetchFromIpApi(ip);
        if (lookedUp == null) {
            lookedUp = fetchFromIpWhoIs(ip);
        }

        if (lookedUp != null) {
            if (geoCache.size() > 10_000) geoCache.clear();
            geoCache.put(subnetKey, lookedUp);
            return mergeHeaderWithGeo(cfCountry, cfRegion, cfCity, cfLat, cfLon, lookedUp);
        }

        String conf = determineConfidence(cfCountry, cfRegion, cfCity, false);
        return new GeoData(
                cfCountry != null ? cfCountry : "XX",
                cfRegion != null ? cfRegion : "Unknown",
                cfCity != null ? cfCity : "Unknown",
                cfLat, cfLon, null, null, false, false, null, conf);
    }

    private GeoData fetchFromIpApi(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,countryCode,regionName,city,lat,lon,isp,org,as,proxy,hosting,timezone"))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode n = objectMapper.readTree(response.body());
                if ("success".equalsIgnoreCase(n.path("status").asText())) {
                    String country = textOrDefault(n, "countryCode", "XX");
                    String region = textOrDefault(n, "regionName", "Unknown");
                    String city = textOrDefault(n, "city", "Unknown");
                    Double lat = n.hasNonNull("lat") ? n.get("lat").asDouble() : null;
                    Double lon = n.hasNonNull("lon") ? n.get("lon").asDouble() : null;
                    String isp = simplifyIspName(textOrDefault(n, "isp", textOrDefault(n, "org", null)));
                    String asn = textOrDefault(n, "as", null);
                    boolean proxy = n.path("proxy").asBoolean(false);
                    boolean hosting = n.path("hosting").asBoolean(false) || isDatacenterIsp(isp, asn);
                    String tz = textOrDefault(n, "timezone", null);
                    String conf = determineConfidence(country, region, city, proxy || hosting);
                    return new GeoData(country, region, city, lat, lon, isp, asn, proxy, hosting, tz, conf);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // fall through to next provider
        }
        return null;
    }

    private GeoData fetchFromIpWhoIs(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ipwho.is/" + ip))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode n = objectMapper.readTree(response.body());
                if (n.path("success").asBoolean(false)) {
                    String country = textOrDefault(n, "country_code", "XX");
                    String region = textOrDefault(n, "region", "Unknown");
                    String city = textOrDefault(n, "city", "Unknown");
                    Double lat = n.hasNonNull("latitude") ? n.get("latitude").asDouble() : null;
                    Double lon = n.hasNonNull("longitude") ? n.get("longitude").asDouble() : null;
                    JsonNode conn = n.path("connection");
                    String isp = simplifyIspName(textOrDefault(conn, "isp", textOrDefault(conn, "org", null)));
                    String asn = conn.hasNonNull("asn") ? "AS" + conn.get("asn").asText() : null;
                    boolean datacenter = isDatacenterIsp(isp, asn);
                    String tz = textOrDefault(n.path("timezone"), "id", null);
                    String conf = determineConfidence(country, region, city, datacenter);
                    return new GeoData(country, region, city, lat, lon, isp, asn, false, datacenter, tz, conf);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // no provider answered
        }
        return null;
    }

    private GeoData mergeHeaderWithGeo(String cfCountry, String cfRegion, String cfCity, Double cfLat, Double cfLon, GeoData geo) {
        String country = (cfCountry != null && !"XX".equalsIgnoreCase(cfCountry)) ? cfCountry : geo.country();
        String region = (cfRegion != null && !"Unknown".equalsIgnoreCase(cfRegion)) ? cfRegion : geo.region();
        String city = (cfCity != null && !"Unknown".equalsIgnoreCase(cfCity)) ? cfCity : geo.city();
        Double lat = cfLat != null ? cfLat : geo.lat();
        Double lon = cfLon != null ? cfLon : geo.lon();
        String conf = determineConfidence(country, region, city, geo.vpnOrProxy() || geo.datacenter());
        return new GeoData(country, region, city, lat, lon, geo.isp(), geo.asn(), geo.vpnOrProxy(), geo.datacenter(), geo.ipTimezone(), conf);
    }

    private String determineConfidence(String country, String region, String city, boolean suspectVpn) {
        if (suspectVpn) return "LOW_VPN";
        boolean hasCountry = country != null && !"XX".equalsIgnoreCase(country) && !"Unknown".equalsIgnoreCase(country);
        boolean hasRegion = region != null && !"Unknown".equalsIgnoreCase(region);
        boolean hasCity = city != null && !"Unknown".equalsIgnoreCase(city);
        if (hasCity && hasRegion && hasCountry) return "EXACT_CITY";
        if (hasRegion && hasCountry) return "STATE_ONLY";
        if (hasCountry) return "COUNTRY_ONLY";
        return "UNKNOWN";
    }

    private boolean isTimezoneMismatch(String browserTz, String countryCode, String ipTz) {
        if (browserTz == null || browserTz.isBlank() || countryCode == null || "XX".equalsIgnoreCase(countryCode)) {
            return false;
        }
        String bTz = browserTz.trim().toLowerCase(Locale.ROOT);
        boolean browserIsIndian = bTz.contains("kolkata") || bTz.contains("calcutta") || bTz.contains("asia/colombo");
        if (browserIsIndian && !"IN".equalsIgnoreCase(countryCode) && !"LK".equalsIgnoreCase(countryCode)) {
            return true;
        }
        if ("IN".equalsIgnoreCase(countryCode) && !browserIsIndian && !bTz.startsWith("asia/")) {
            return true;
        }
        if (ipTz != null && !ipTz.isBlank()) {
            String iTz = ipTz.trim().toLowerCase(Locale.ROOT);
            String bContinent = bTz.contains("/") ? bTz.split("/")[0] : bTz;
            String iContinent = iTz.contains("/") ? iTz.split("/")[0] : iTz;
            return !bContinent.equals(iContinent);
        }
        return false;
    }

    private String simplifyIspName(String rawIsp) {
        if (rawIsp == null || rawIsp.isBlank()) return null;
        String l = rawIsp.toLowerCase(Locale.ROOT);
        if (l.contains("jio")) return "Reliance Jio";
        if (l.contains("bharti") || l.contains("airtel")) return "Airtel";
        if (l.contains("vodafone") || l.contains("idea")) return "Vi (Vodafone Idea)";
        if (l.contains("atria") || l.contains("act fibernet") || l.contains("beam telecom")) return "ACT Fibernet";
        if (l.contains("bsnl") || l.contains("bharat sanchar")) return "BSNL";
        if (l.contains("tata")) return "Tata Communications / Fiber";
        if (l.contains("hathway")) return "Hathway";
        if (l.contains("excitel")) return "Excitel";
        if (l.contains("you broadband")) return "YOU Broadband";
        return clip(rawIsp, 128);
    }

    private boolean isDatacenterIsp(String isp, String asn) {
        String combined = ((isp != null ? isp : "") + " " + (asn != null ? asn : "")).toLowerCase(Locale.ROOT);
        for (String kw : DATACENTER_KEYWORDS) {
            if (combined.contains(kw)) return true;
        }
        return false;
    }

    // =====================================================================
    // DEEPER ANALYTICS HELPERS
    // =====================================================================

    private List<Map<String, Object>> getLiveVisitorsNow() {
        return jdbc.queryForList("""
            SELECT
                COALESCE(s.person_id, s.visitor_hash) AS person_id,
                COALESCE(s.user_name, s.user_email, s.friendly_name, 'Visitor') AS name,
                s.display_label,
                s.user_type,
                COALESCE(s.last_path, s.entry_path) AS current_page,
                s.city,
                s.region,
                s.country,
                s.isp,
                s.device_type,
                s.browser,
                s.active_dwell_seconds,
                s.last_active_at
            FROM public.analytics_sessions s
            WHERE s.last_active_at >= NOW() - INTERVAL '5 minutes'
            ORDER BY s.last_active_at DESC
            LIMIT 25
            """);
    }

    private List<Map<String, Object>> getVisitFrequencyGroups(int days) {
        return jdbc.queryForList("""
            WITH person_counts AS (
                SELECT COALESCE(person_id, visitor_hash) AS pid, COUNT(*) AS visits
                FROM public.analytics_sessions
                WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY COALESCE(person_id, visitor_hash)
            )
            SELECT
                CASE
                    WHEN visits = 1 THEN '1 visit'
                    WHEN visits BETWEEN 2 AND 3 THEN '2 to 3 visits'
                    ELSE '4+ visits'
                END AS frequency_group,
                COUNT(*) AS people
            FROM person_counts
            GROUP BY 1
            ORDER BY MIN(visits) ASC
            """, days);
    }

    private List<Map<String, Object>> getUserTypeSplit(int days) {
        return jdbc.queryForList("""
            SELECT
                COALESCE(user_type, 'GUEST') AS user_type,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(user_type, 'GUEST')
            ORDER BY unique_people DESC
            """, days);
    }

    private List<Map<String, Object>> getFirstVsLastAttribution(int days) {
        return jdbc.queryForList("""
            SELECT
                COALESCE(first_channel, 'Direct') AS first_source,
                COALESCE(last_channel, 'Direct') AS last_source,
                COUNT(*) AS people,
                COUNT(*) FILTER (WHERE applied = TRUE) AS converted_people
            FROM public.analytics_visitors
            WHERE last_seen_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(first_channel, 'Direct'), COALESCE(last_channel, 'Direct')
            ORDER BY converted_people DESC, people DESC
            LIMIT 15
            """, days);
    }

    private List<Map<String, Object>> getIstHourlyHeatmap(int days) {
        return jdbc.queryForList("""
            SELECT
                EXTRACT(DOW FROM (created_at AT TIME ZONE 'Asia/Kolkata'))::int AS day_of_week,
                to_char(created_at AT TIME ZONE 'Asia/Kolkata', 'Dy') AS day_name,
                EXTRACT(HOUR FROM (created_at AT TIME ZONE 'Asia/Kolkata'))::int AS hour_ist,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY 1, 2, 3
            ORDER BY 1 ASC, 3 ASC
            """, days);
    }

    private Map<String, Object> getTimeToConvertStats(int days) {
        return jdbc.queryForMap("""
            SELECT
                COUNT(*) FILTER (WHERE applied = TRUE AND first_applied_at IS NOT NULL) AS total_converted_people,
                ROUND(CAST(COALESCE(AVG(EXTRACT(EPOCH FROM (first_applied_at - first_seen_at)) / 86400.0) FILTER (WHERE applied = TRUE AND first_applied_at >= first_seen_at), 0) AS NUMERIC), 2) AS avg_days_to_convert,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) < INTERVAL '1 day') AS same_day_conversions,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) >= INTERVAL '1 day' AND (first_applied_at - first_seen_at) <= INTERVAL '3 days') AS days_1_to_3,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) > INTERVAL '3 days') AS days_4_plus
            FROM public.analytics_visitors
            WHERE last_seen_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """, days);
    }

    private List<Map<String, Object>> getHotVisitors(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                person_id, user_name, user_email, user_type, friendly_name, display_label,
                city, region, country, isp, first_channel, last_channel,
                total_sessions, total_page_views, total_active_seconds,
                campaign_views_count, apply_clicks_count, lead_score,
                applied, admin_notes, admin_tags, last_seen_at
            FROM public.analytics_visitors
            ORDER BY lead_score DESC, last_seen_at DESC
            LIMIT ?
            """, limit);
        for (Map<String, Object> r : rows) {
            r.put("primaryDisplayName", resolvePrimaryName(r));
            r.put("quickTags", buildQuickTags(r));
        }
        return rows;
    }

    private List<Map<String, Object>> getCampaignLevelFunnels(int days) {
        return jdbc.queryForList("""
            WITH camp_events AS (
                SELECT
                    campaign_id,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type IN ('PAGE_VIEW', 'CAMPAIGN_VIEW')) AS viewed_people,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type = 'APPLY_CLICK') AS apply_clicked_people,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type = 'FORM_SUBMIT') AS submitted_people
                FROM public.analytics_events
                WHERE campaign_id IS NOT NULL
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY campaign_id
            ),
            camp_sessions AS (
                SELECT
                    campaign_id,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS session_visitors,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FILTER (WHERE applied = TRUE) AS session_applied
                FROM public.analytics_sessions
                WHERE campaign_id IS NOT NULL
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY campaign_id
            )
            SELECT
                CAST(COALESCE(cs.campaign_id, ce.campaign_id) AS VARCHAR) AS campaign_id,
                COALESCE(mc.title, c.campaign_title, 'Campaign ' || SUBSTRING(CAST(COALESCE(cs.campaign_id, ce.campaign_id) AS VARCHAR), 1, 8)) AS campaign_title,
                GREATEST(COALESCE(cs.session_visitors, 0), COALESCE(ce.viewed_people, 0)) AS viewed_count,
                GREATEST(COALESCE(ce.apply_clicked_people, 0), COALESCE(cs.session_applied, 0)) AS apply_clicked_count,
                GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)) AS applied_count,
                COALESCE(sp.spend_inr, 0) AS spend_inr,
                CASE
                    WHEN GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)) > 0 AND COALESCE(sp.spend_inr, 0) > 0
                    THEN ROUND(sp.spend_inr / GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)), 2)
                    ELSE 0
                END AS cost_per_apply_inr
            FROM camp_sessions cs
            FULL OUTER JOIN camp_events ce ON ce.campaign_id = cs.campaign_id
            LEFT JOIN public.managed_campaigns mc ON mc.id = COALESCE(cs.campaign_id, ce.campaign_id)
            LEFT JOIN public.campaigns c ON c.id = COALESCE(cs.campaign_id, ce.campaign_id)
            LEFT JOIN public.analytics_campaign_spend sp ON sp.campaign_id = COALESCE(cs.campaign_id, ce.campaign_id)
            ORDER BY viewed_count DESC
            LIMIT 25
            """, days, days);
    }

    private List<Map<String, Object>> getSameNetworkGroups() {
        return jdbc.queryForList("""
            SELECT
                network_group_id,
                COALESCE(isp, 'Shared Network') AS isp,
                COALESCE(city, 'Unknown') AS city,
                MAX(masked_ip) AS masked_ip,
                COUNT(*) AS distinct_visitors,
                SUM(total_sessions) AS total_sessions
            FROM public.analytics_visitors
            WHERE network_group_id IS NOT NULL
            GROUP BY network_group_id, COALESCE(isp, 'Shared Network'), COALESCE(city, 'Unknown')
            HAVING COUNT(*) >= 2
            ORDER BY distinct_visitors DESC
            LIMIT 15
            """);
    }

    private List<Map<String, Object>> getPossibleDuplicateHints() {
        return jdbc.queryForList("""
            SELECT
                v1.person_id AS person_a,
                v1.friendly_name AS name_a,
                v1.display_label AS label_a,
                v2.person_id AS person_b,
                v2.friendly_name AS name_b,
                v2.display_label AS label_b,
                v1.city,
                v1.isp,
                'Same device screen, OS, timezone & network within 24h (likely In-App to Browser switch)' AS reason
            FROM public.analytics_visitors v1
            JOIN public.analytics_visitors v2
              ON v1.device_fingerprint = v2.device_fingerprint
             AND v1.network_group_id = v2.network_group_id
             AND v1.person_id < v2.person_id
             AND ABS(EXTRACT(EPOCH FROM (v1.last_seen_at - v2.last_seen_at))) <= 86400
            WHERE v1.device_fingerprint IS NOT NULL
            ORDER BY v1.last_seen_at DESC
            LIMIT 10
            """);
    }

    private List<Map<String, Object>> checkTrafficAnomalies() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        long lastHour = queryLong("SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FROM public.analytics_sessions WHERE created_at >= NOW() - INTERVAL '1 hour'");
        long prev7dHourlyAvg = Math.max(1, queryLong("SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FROM public.analytics_sessions WHERE created_at >= NOW() - INTERVAL '7 days'") / (7 * 24));

        if (lastHour >= prev7dHourlyAvg * 3 && lastHour >= 10) {
            String msg = "Traffic spike: " + lastHour + " unique people in the last hour (normal hourly avg: " + prev7dHourlyAvg + ").";
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "TRAFFIC_SPIKE");
            alert.put("severity", "HIGH");
            alert.put("message", msg);
            alerts.add(alert);
            sendOptionalTelegramAlert(msg);
        }
        return alerts;
    }

    private void sendOptionalTelegramAlert(String text) {
        if (telegramBotToken == null || telegramBotToken.isBlank() || telegramChatId == null || telegramChatId.isBlank()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + telegramBotToken.trim() + "/sendMessage";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", telegramChatId.trim());
            payload.put("text", "📊 BrandCollabz Analytics: " + text);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(1500))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // alerts are best-effort
        }
    }

    // =====================================================================
    // HELPERS: BOT FILTER, FRIENDLY NAMES, IP MASKING, HASHING
    // =====================================================================

    private boolean shouldIgnoreTraffic(HttpServletRequest req, String ua, String ip, String email) {
        if ("true".equalsIgnoreCase(req.getHeader("X-Analytics-Ignore"))) {
            return true;
        }
        if (ua == null || ua.isBlank()) {
            return true;
        }
        String lowerUa = ua.toLowerCase(Locale.ROOT);
        for (String bot : BOT_SIGNATURES) {
            if (lowerUa.contains(bot)) return true;
        }
        if (excludedIpsConfig != null && !excludedIpsConfig.isBlank() && ip != null) {
            for (String exIp : excludedIpsConfig.split(",")) {
                if (!exIp.isBlank() && ip.trim().equalsIgnoreCase(exIp.trim())) return true;
            }
        }
        if (excludedEmailsConfig != null && !excludedEmailsConfig.isBlank() && email != null) {
            for (String exEmail : excludedEmailsConfig.split(",")) {
                if (!exEmail.isBlank() && email.trim().equalsIgnoreCase(exEmail.trim())) return true;
            }
        }
        return false;
    }

    /** personId can start with "acc_"/"usr_", so we hash it first to always get valid hex for the name/tag. */
    private String generateFriendlyName(String personId) {
        String h = hashIdentifier("fn|" + personId);
        int adjIdx = Integer.parseInt(h.substring(0, 2), 16) % ADJECTIVES.length;
        int aniIdx = Integer.parseInt(h.substring(2, 4), 16) % ANIMALS.length;
        return ADJECTIVES[adjIdx] + " " + ANIMALS[aniIdx] + " " + shortTagFor(personId);
    }

    private String shortTagFor(String personId) {
        return "#" + hashIdentifier("fn|" + personId).substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private String resolvePrimaryName(Map<String, Object> row) {
        Object name = row.get("user_name");
        Object email = row.get("user_email");
        Object type = row.get("user_type");
        String roleSuffix = (type != null && !"GUEST".equalsIgnoreCase(String.valueOf(type)))
                ? " (" + String.valueOf(type).toLowerCase(Locale.ROOT) + ")" : "";
        if (name != null && !String.valueOf(name).isBlank()) {
            return name + roleSuffix;
        }
        if (email != null && !String.valueOf(email).isBlank()) {
            return email + roleSuffix;
        }
        Object friendly = row.get("friendly_name");
        return friendly != null ? String.valueOf(friendly) : "Anonymous Visitor";
    }

    private List<String> buildQuickTags(Map<String, Object> row) {
        List<String> tags = new ArrayList<>();
        int sessions = row.get("total_sessions") instanceof Number n ? n.intValue() : 1;
        int score = row.get("lead_score") instanceof Number n2 ? n2.intValue() : 0;
        boolean applied = Boolean.TRUE.equals(row.get("applied"));
        boolean vpn = Boolean.TRUE.equals(row.get("is_vpn_or_proxy"))
                || Boolean.TRUE.equals(row.get("is_datacenter"))
                || Boolean.TRUE.equals(row.get("timezone_mismatch"));

        tags.add(sessions > 1 ? "RETURNING" : "NEW");
        if (applied) tags.add("APPLIED");
        if (score >= 60) tags.add("HIGH_INTEREST");
        if (vpn) tags.add("VPN_OR_PROXY");
        return tags;
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "0.0.x.x";
        String clean = ip.trim();
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".x.x";
            }
        }
        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":x:x";
            }
        }
        return "hidden";
    }

    private String extractSubnetPrefix(String ip) {
        if (ip == null || ip.isBlank()) return "0.0.0";
        String clean = ip.trim();
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + "." + parts[2];
        }
        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            if (parts.length >= 3) return parts[0] + ":" + parts[1] + ":" + parts[2];
        }
        return clean;
    }

    private boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        String clean = ip.trim();
        return "127.0.0.1".equals(clean) || "0:0:0:0:0:0:0:1".equals(clean) || "::1".equals(clean)
                || clean.startsWith("10.") || clean.startsWith("192.168.")
                || clean.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    /** Hashes ONLY visitorId (never mixes in IP), so one person across WiFi & mobile data has 1 stable ID. */
    private String hashStableVisitor(String visitorId) {
        return hashIdentifier("vid|" + visitorId.trim());
    }

    private String hashIdentifier(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(visitorSalt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash identifier", e);
        }
    }

    private long queryLong(String sql, Object... args) {
        Long val = jdbc.queryForObject(sql, Long.class, args);
        return val != null ? val : 0L;
    }

    private static double num(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : defaultValue;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String cleanPath(String raw) {
        String p = raw == null ? "/" : raw.trim();
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#');
        if (h >= 0) p = p.substring(0, h);
        if (p.isEmpty()) p = "/";
        return p.length() > 512 ? p.substring(0, 512) : p;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String extractDomain(String url) {
        if (url == null) return null;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return clip(host, 255);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String headerOrNull(HttpServletRequest req, String h1, String h2, String h3) {
        for (String h : new String[]{h1, h2, h3}) {
            if (h == null) continue;
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private Double parseDoubleOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            String v = node.get(field).asText().trim();
            if (!v.isEmpty()) return v;
        }
        return fallback;
    }

    private String extractLanguage(HttpServletRequest req) {
        String h = req.getHeader("Accept-Language");
        if (h == null || h.isBlank()) return null;
        String first = h.split(",")[0].split(";")[0].trim();
        return clip(first, 16);
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("True-Client-IP");
        }
        if (ip == null || ip.isBlank()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                ip = xff.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip != null ? ip : "0.0.0.0";
    }

    private String detectDeviceType(String ua) {
        if (ua == null) return "Desktop";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("ipad") || l.contains("tablet")) return "Tablet";
        if (l.contains("android") && !l.contains("mobile")) return "Tablet";
        if (l.contains("mobile") || l.contains("android") || l.contains("iphone")) return "Mobile";
        return "Desktop";
    }

    private String detectOs(String ua) {
        if (ua == null) return "Other";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("iphone") || l.contains("ipad") || l.contains("ipod")) return "iOS";
        if (l.contains("android")) return "Android";
        if (l.contains("windows")) return "Windows";
        if (l.contains("mac os x") || l.contains("macintosh")) return "macOS";
        if (l.contains("linux")) return "Linux";
        return "Other";
    }

    private String detectBrowser(String ua) {
        if (ua == null) return "Other";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("instagram")) return "Instagram In-App";
        if (l.contains("fban") || l.contains("fbav") || l.contains("fb_iab")) return "Facebook In-App";
        if (l.contains("edg/") || l.contains("edga/") || l.contains("edgios/")) return "Edge";
        if (l.contains("opr/") || l.contains("opera")) return "Opera";
        if (l.contains("samsungbrowser")) return "Samsung Internet";
        if (l.contains("firefox") || l.contains("fxios")) return "Firefox";
        if (l.contains("chrome") || l.contains("crios")) return "Chrome";
        if (l.contains("safari")) return "Safari";
        return "Other";
    }

    private String detectInAppSource(String ua, String referrer, String deviceType) {
        String l = ua == null ? "" : ua.toLowerCase(Locale.ROOT);
        if (l.contains("instagram")) return "Instagram";
        if (l.contains("fban") || l.contains("fbav") || l.contains("fb_iab")) return "Facebook";
        if (l.contains("whatsapp")) return "WhatsApp";
        if (l.contains("linkedinapp")) return "LinkedIn";
        if (l.contains("snapchat")) return "Snapchat";
        if ("Mobile".equals(deviceType) && referrer != null) {
            String r = referrer.toLowerCase(Locale.ROOT);
            if (r.contains("instagram.com")) return "Instagram";
            if (r.contains("whatsapp") || r.contains("wa.me")) return "WhatsApp";
        }
        return null;
    }

    private String classifyChannel(String utmSource, String utmMedium, String referrerDomain, String inAppSource) {
        String src = utmSource == null ? "" : utmSource.toLowerCase(Locale.ROOT);
        String med = utmMedium == null ? "" : utmMedium.toLowerCase(Locale.ROOT);

        if (containsAny(med, "cpc", "ppc", "paid", "display", "cpm")) return "Paid";
        if (containsAny(med, "email", "newsletter") || containsAny(src, "email", "newsletter")) return "Email";
        if (containsAny(med, "influencer", "creator", "affiliate")) return "Influencer";
        if (containsAny(med, "social") || containsAny(src,
                "instagram", "facebook", "whatsapp", "linkedin", "twitter", "youtube", "snapchat", "pinterest", "reddit", "threads")) {
            return "Social";
        }
        if (inAppSource != null) return "Social";

        if (referrerDomain != null) {
            String d = referrerDomain;
            if (containsAny(d, "google.", "bing.", "duckduckgo.", "yahoo.", "ecosia.", "baidu.", "yandex.")) {
                return "Organic Search";
            }
            if (containsAny(d, "instagram.", "facebook.", "fb.com", "t.co", "twitter.", "x.com", "linkedin.",
                    "lnkd.in", "youtube.", "youtu.be", "pinterest.", "reddit.", "snapchat.", "threads.", "wa.me", "whatsapp.")) {
                return "Social";
            }
            return "Referral";
        }
        if (!src.isEmpty() || !med.isEmpty()) return "Other Campaign";
        return "Direct";
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty()) return false;
        for (String n : needles) {
            if (value.contains(n)) return true;
        }
        return false;
    }

    private String csvLine(Object... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            String raw = cols[i] == null ? "" : String.valueOf(cols[i]).replace('\r', ' ').replace('\n', ' ');
            if (!raw.isEmpty() && "=+-@".indexOf(raw.charAt(0)) >= 0) {
                raw = "'" + raw;
            }
            sb.append('"').append(raw.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }
}