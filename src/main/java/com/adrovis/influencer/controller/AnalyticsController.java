package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import com.adrovis.influencer.repository.AnalyticsRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "${analytics.allowed-origins:*}")
@Slf4j
public class AnalyticsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> SORTABLE = Set.of(
            "createdAt", "lastActiveAt", "durationSeconds", "activeDwellSeconds",
            "pageViews", "city", "channel");
    private static final Map<String, String> DEVICES = Map.of(
            "mobile", "Mobile", "desktop", "Desktop", "tablet", "Tablet");

    private final AnalyticsRepository analyticsRepository;

    /** Secret salt for visitor hashing. Set analytics.visitor-salt in production. */
    @Value("${analytics.visitor-salt:change-me-in-production}")
    private String visitorSalt;

    /** Shared secret for admin endpoints (/overview, /sessions). Empty = admin endpoints locked. */
    @Value("${analytics.admin-key:}")
    private String adminKey;

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

        @Size(max = 2048) private String referrer;
        @Size(max = 256)  private String utmSource;
        @Size(max = 256)  private String utmMedium;
        @Size(max = 256)  private String utmCampaign;
        @Size(max = 256)  private String utmContent;
        @Size(max = 256)  private String utmTerm;
        private UUID campaignId;
        @Size(max = 32)   private String screenResolution;
        @Size(max = 64)   private String timezone;
    }

    @Data
    public static class HeartbeatRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @Min(0) @Max(86_400)
        private int durationSeconds;

        @Min(0) @Max(86_400)
        private int activeDwellSeconds;
    }

    @Data
    public static class ConversionRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;
    }

    /** Explicit opt-in only. Submitted from a form the visitor fills in and consents to. */
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

        @Pattern(regexp = "^(BRAND|INFLUENCER|OTHER)$", message = "userType must be BRAND, INFLUENCER or OTHER")
        private String userType;

        @AssertTrue(message = "consent is required")
        private boolean consent;

        @Size(max = 512)
        private String consentText;
    }

    // =====================================================================
    // PUBLIC INGESTION ENDPOINTS
    // =====================================================================

    @PostMapping("/track")
    @Transactional
    public ResponseEntity<ApiResponse<String>> trackVisit(
            @Valid @RequestBody TrackVisitRequest req,
            HttpServletRequest http) {

        String visitorHash = hashVisitor(req.getVisitorId(), extractClientIp(http));
        String ua = http.getHeader("User-Agent");

        String path = cleanPath(req.getPath());
        String referrer = clip(req.getReferrer(), 1024);
        String referrerDomain = extractDomain(referrer);

        String deviceType = detectDeviceType(ua);
        String inAppSource = detectInAppSource(ua, referrer, deviceType);

        String utmSource = clip(req.getUtmSource(), 128);
        String utmMedium = clip(req.getUtmMedium(), 128);
        String channel = classifyChannel(utmSource, utmMedium, referrerDomain, inAppSource);

        String country = headerOrDefault(http, "CF-IPCountry", "X-Country-Code", "XX");
        String region = headerOrDefault(http, "CF-Region", "X-Region-Name", "Unknown");
        String city = headerOrDefault(http, "CF-IPCity", "X-City-Name", "Unknown");

        // 1. Idempotent daily unique visitor (ON CONFLICT DO NOTHING)
        analyticsRepository.recordDailyUniqueVisitor(visitorHash);

        // 2. Atomic session upsert: insert on first call, page_views + 1 on later calls
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
                clip(country, 8),
                clip(region, 128),
                clip(city, 128),
                deviceType,
                detectBrowser(ua),
                detectOs(ua),
                inAppSource != null,
                inAppSource,
                clip(req.getScreenResolution(), 24),
                clip(req.getTimezone(), 64),
                extractLanguage(http));

        return ResponseEntity.ok(ApiResponse.ok("Tracked"));
    }

    @PostMapping("/heartbeat")
    @Transactional
    public ResponseEntity<ApiResponse<String>> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
        analyticsRepository.updateHeartbeat(
                req.getSessionId(),
                Math.max(0, req.getDurationSeconds()),
                Math.max(0, req.getActiveDwellSeconds()));
        return ResponseEntity.ok(ApiResponse.ok("Alive"));
    }

    @PostMapping("/conversion")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordConversion(@Valid @RequestBody ConversionRequest req) {
        analyticsRepository.markApplicationConverted(req.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok("Conversion recorded"));
    }

    /** Opt-in lead capture, linked to the session so you can see which channel/campaign produced it. */
    @PostMapping("/lead")
    @Transactional
    public ResponseEntity<ApiResponse<String>> captureLead(@Valid @RequestBody LeadRequest req) {
        int rows = analyticsRepository.upsertLead(
                req.getSessionId(),
                req.getEmail().trim().toLowerCase(Locale.ROOT),
                clip(req.getFullName(), 120),
                clip(req.getPhone(), 20),
                req.getUserType(),
                clip(req.getConsentText(), 512));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session");
        }
        return ResponseEntity.ok(ApiResponse.ok("Lead saved"));
    }

    // =====================================================================
    // ADMIN ENDPOINTS
    // =====================================================================

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey,
            @RequestParam(defaultValue = "30") int days) {

        requireAdmin(providedKey);
        int window = Math.min(Math.max(days, 1), 365);

        long liveUsers = analyticsRepository.countLiveActiveUsers();
        long liveCampaigns = analyticsRepository.countLiveViewingCampaigns();
        long todayVisitors = analyticsRepository.countTodayUniqueVisitors();
        long monthVisitors = analyticsRepository.countThisMonthUniqueVisitors();
        long allTimeVisitors = analyticsRepository.countTotalAllTimeUniqueVisitors();

        Map<String, Object> s = analyticsRepository.getSessionSummary(window);
        long totalSessions = (long) num(s, "total_sessions", 0);
        long appliedSessions = (long) num(s, "applied_sessions", 0);
        double avgDuration = num(s, "avg_duration", 0);
        double avgActive = num(s, "avg_active", 0);
        double avgPages = num(s, "avg_pages", 0);
        double bounce = num(s, "bounce_rate", 0);
        double mobilePct = num(s, "mobile_pct", 0);
        double desktopPct = num(s, "desktop_pct", 0);
        double tabletPct = num(s, "tablet_pct", 0);
        double inAppPct = num(s, "in_app_pct", 0);
        double returningPct = num(s, "returning_pct", 0);

        long leads = analyticsRepository.countLeadsSince(window);
        double convRate = totalSessions > 0 ? round1(appliedSessions * 100.0 / totalSessions) : 0.0;
        double leadRate = totalSessions > 0 ? round1(leads * 100.0 / totalSessions) : 0.0;

        int durMin = (int) (avgDuration / 60);
        int durSec = (int) (avgDuration % 60);
        int activeMin = (int) (avgActive / 60);
        int activeSec = (int) (avgActive % 60);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowDays", window);

        Map<String, Object> live = new LinkedHashMap<>();
        live.put("activeUsersRightNow", liveUsers);
        live.put("currentlyViewingCampaigns", liveCampaigns);
        out.put("live", live);

        Map<String, Object> traffic = new LinkedHashMap<>();
        traffic.put("todayUniqueVisitors", todayVisitors);
        traffic.put("thisMonthUniqueVisitors", monthVisitors);
        traffic.put("allTimeUniqueVisitors", allTimeVisitors);
        traffic.put("sessionsInWindow", totalSessions);
        out.put("trafficVolume", traffic);

        Map<String, Object> engagement = new LinkedHashMap<>();
        engagement.put("averageSessionDurationSeconds", Math.round(avgDuration));
        engagement.put("averageEngagementDisplay", String.format(Locale.ROOT, "%dm %02ds", durMin, durSec));
        engagement.put("averageActiveDwellSeconds", Math.round(avgActive));
        engagement.put("averageActiveDwellDisplay", String.format(Locale.ROOT, "%dm %02ds", activeMin, activeSec));
        engagement.put("bounceRatePercentage", round1(bounce));            // < 10s and single page view
        engagement.put("averagePageViewsPerSession", round1(avgPages));
        out.put("engagement", engagement);

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("mobilePercentage", round1(mobilePct));
        env.put("desktopPercentage", round1(desktopPct));
        env.put("tabletPercentage", round1(tabletPct));
        env.put("inAppBrowserPercentage", round1(inAppPct));
        env.put("returningVisitorPercentage", round1(returningPct));
        env.put("newVisitorPercentage", totalSessions > 0 ? round1(100.0 - returningPct) : 0.0);
        out.put("environment", env);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("sessions", totalSessions);
        funnel.put("applyButtonClicks", appliedSessions);
        funnel.put("overallConversionRate", convRate);
        funnel.put("optInLeads", leads);
        funnel.put("leadCaptureRate", leadRate);
        out.put("funnelConversion", funnel);

        out.put("topTrafficChannels", safe(analyticsRepository.getTopTrafficChannels(window)));
        out.put("topCampaigns", safe(analyticsRepository.getTopCampaigns(window)));
        out.put("topReferrers", safe(analyticsRepository.getTopReferrers(window)));
        out.put("topLandingPages", safe(analyticsRepository.getTopLandingPages(window)));
        out.put("topCities", safe(analyticsRepository.getTopCities(window)));
        out.put("dailyVisitorTrend", safe(analyticsRepository.getDailyVisitorTrend(window)));

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<AnalyticsSessionEntity>>> getSessions(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean applied,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean activeNow,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        requireAdmin(providedKey);

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
                        cb.like(cb.lower(root.<String>get("entryPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("lastPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("referrer")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmSource")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmMedium")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmCampaign")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmContent")), like, '\\')));
            }
            if (device != null) {
                p.add(cb.equal(root.<String>get("deviceType"), device));
            }
            if (channelFilter != null) {
                p.add(cb.equal(cb.lower(root.<String>get("channel")), channelFilter.toLowerCase(Locale.ROOT)));
            }
            if (applied != null) {
                p.add(cb.equal(root.<Boolean>get("applied"), applied));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"),
                        from.atStartOfDay(IST).toOffsetDateTime()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.<OffsetDateTime>get("createdAt"),
                        to.plusDays(1).atStartOfDay(IST).toOffsetDateTime()));
            }
            if (Boolean.TRUE.equals(activeNow)) {                         // <-- ADD THIS BLOCK
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("lastActiveAt"),
                        OffsetDateTime.now().minusMinutes(5)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        String sortField = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortField).and(Sort.by(Sort.Direction.DESC, "sessionId")); // stable paging

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        Page<AnalyticsSessionEntity> result = analyticsRepository.findAll(spec, pageable);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/sessions/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<AnalyticsSessionEntity>> getSession(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey,
            @PathVariable UUID id) {
        requireAdmin(providedKey);
        return ResponseEntity.ok(ApiResponse.ok(analyticsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"))));
    }

    @GetMapping("/leads")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLeads(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey) {
        requireAdmin(providedKey);
        return ResponseEntity.ok(ApiResponse.ok(safe(analyticsRepository.getRecentLeads())));
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private void requireAdmin(String provided) {
        boolean ok = adminKey != null && !adminKey.isBlank() && provided != null
                && MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
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

    /** Drops query string and fragment so tokens or personal data in URLs are never stored. */
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

    private String headerOrDefault(HttpServletRequest req, String primary, String secondary, String fallback) {
        String v = req.getHeader(primary);
        if (v == null || v.isBlank()) v = req.getHeader(secondary);
        return (v == null || v.isBlank()) ? fallback : v.trim();
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
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                ip = xff.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip != null ? ip : "0.0.0.0";
    }

    /** HMAC-SHA256(salt, visitorId|ip). The raw IP is never stored. */
    private String hashVisitor(String visitorId, String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(visitorSalt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((visitorId + "|" + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash visitor", e);
        }
    }

    // ---------- user-agent parsing ----------

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

    /** Returns Instagram / Facebook / WhatsApp / LinkedIn / Snapchat, or null if not an in-app browser. */
    private String detectInAppSource(String ua, String referrer, String deviceType) {
        String l = ua == null ? "" : ua.toLowerCase(Locale.ROOT);
        if (l.contains("instagram")) return "Instagram";
        if (l.contains("fban") || l.contains("fbav") || l.contains("fb_iab")) return "Facebook";
        if (l.contains("whatsapp")) return "WhatsApp";
        if (l.contains("linkedinapp")) return "LinkedIn";
        if (l.contains("snapchat")) return "Snapchat";
        // Referrer fallback, only on mobile (a desktop click from instagram.com is not an in-app browser)
        if ("Mobile".equals(deviceType) && referrer != null) {
            String r = referrer.toLowerCase(Locale.ROOT);
            if (r.contains("instagram.com")) return "Instagram";
            if (r.contains("whatsapp") || r.contains("wa.me")) return "WhatsApp";
        }
        return null;
    }

    // ---------- marketing channel classification ----------

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
}