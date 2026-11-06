package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import com.adrovis.influencer.repository.AnalyticsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;

    @Data
    public static class TrackVisitRequest {
        @NotBlank(message = "visitorId is required")
        private String visitorId;

        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @NotBlank(message = "path is required")
        private String path;

        private String referrer;
        private String utmSource;
        private String utmMedium;
        private String utmCampaign;
        private UUID campaignId;
        private String screenResolution;
        private String timezone;
    }

    @Data
    public static class HeartbeatRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;
        private int durationSeconds;
        private int activeDwellSeconds;
    }

    @Data
    public static class ConversionRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;
    }

    @PostMapping("/track")
    @Transactional
    public ResponseEntity<ApiResponse<String>> trackVisit(
            @Valid @RequestBody TrackVisitRequest req,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        String visitorHash = hashVisitor(req.getVisitorId(), clientIp);

        // 1. Idempotent daily visitor record
        analyticsRepository.recordDailyUniqueVisitor(visitorHash);

        // 2. Register or update session
        if (!analyticsRepository.existsById(req.getSessionId())) {
            String userAgent = httpRequest.getHeader("User-Agent");
            boolean isInApp = isInAppBrowser(userAgent, req.getReferrer());
            String deviceType = detectDeviceType(userAgent);
            String browser = detectBrowser(userAgent);

            String country = headerOrDefault(httpRequest, "CF-IPCountry", "X-Country-Code", "IN");
            String city = headerOrDefault(httpRequest, "CF-IPCity", "X-City-Name", "Mumbai");
            String region = headerOrDefault(httpRequest, "CF-Region", "X-Region-Name", "Maharashtra");

            AnalyticsSessionEntity session = AnalyticsSessionEntity.builder()
                    .sessionId(req.getSessionId())
                    .visitorHash(visitorHash)
                    .entryPath(req.getPath())
                    .referrer(req.getReferrer())
                    .utmSource(req.getUtmSource())
                    .utmMedium(req.getUtmMedium())
                    .utmCampaign(req.getUtmCampaign())
                    .campaignId(req.getCampaignId())
                    .country(country)
                    .region(region)
                    .city(city)
                    .deviceType(deviceType)
                    .browser(browser)
                    .isInAppBrowser(isInApp)
                    .screenResolution(req.getScreenResolution())
                    .timezone(req.getTimezone())
                    .lastActiveAt(OffsetDateTime.now())
                    .durationSeconds(0)
                    .activeDwellSeconds(0)
                    .pageViews(1)
                    .build();

            analyticsRepository.save(session);
        } else {
            analyticsRepository.incrementSessionPageViews(req.getSessionId());
        }

        return ResponseEntity.ok(ApiResponse.ok("Tracked"));
    }

    @PostMapping("/heartbeat")
    @Transactional
    public ResponseEntity<ApiResponse<String>> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
        analyticsRepository.updateHeartbeat(
                req.getSessionId(),
                Math.max(0, req.getDurationSeconds()),
                Math.max(0, req.getActiveDwellSeconds())
        );
        return ResponseEntity.ok(ApiResponse.ok("Alive"));
    }

    @PostMapping("/conversion")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordConversion(@Valid @RequestBody ConversionRequest req) {
        analyticsRepository.markApplicationConverted(req.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok("Conversion recorded"));
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        OffsetDateTime fiveMinAgo = OffsetDateTime.now().minusMinutes(5);

        long liveUsers = analyticsRepository.countLiveActiveUsers(fiveMinAgo);
        long liveCampaigns = analyticsRepository.countLiveViewingCampaigns(fiveMinAgo);

        long todayVisitors = analyticsRepository.countTodayUniqueVisitors();
        long monthVisitors = analyticsRepository.countThisMonthUniqueVisitors();
        long allTimeVisitors = analyticsRepository.countTotalAllTimeUniqueVisitors();

        Map<String, Object> eng = analyticsRepository.getOverallEngagementMetrics();
        double avgDuration = num(eng, "avg_duration", 0.0);
        double avgActive = num(eng, "avg_active", 0.0);
        double bounce = num(eng, "bounce_rate", 0.0);
        double avgPages = num(eng, "avg_pages", 1.0);

        int durMin = (int) (avgDuration / 60);
        int durSec = (int) (avgDuration % 60);

        List<Map<String, Object>> trafficSources = analyticsRepository.getTopTrafficSources();
        List<Map<String, Object>> campaignAttribution = analyticsRepository.getTopCampaignAttribution();
        List<Map<String, Object>> geo = analyticsRepository.getTopGeographicLocations();

        Map<String, Object> env = analyticsRepository.getEnvironmentAndConversionMetrics();
        double mobilePct = round1(num(env, "mobile_pct", 85.0));
        double inAppPct = round1(num(env, "in_app_pct", 60.0));
        long totalApplies = (long) num(env, "total_applies", 0.0);
        long totalViews = (long) num(env, "total_campaign_views", 0.0);

        double convRate = totalViews > 0 ? round1(totalApplies * 100.0 / totalViews) : 0.0;

        Map<String, Object> responseData = new LinkedHashMap<>();

        Map<String, Object> liveMap = new LinkedHashMap<>();
        liveMap.put("activeUsersRightNow", liveUsers);
        liveMap.put("currentlyViewingCampaigns", liveCampaigns);
        responseData.put("live", liveMap);

        Map<String, Object> trafficMap = new LinkedHashMap<>();
        trafficMap.put("todayUniqueVisitors", todayVisitors);
        trafficMap.put("thisMonthUniqueVisitors", monthVisitors);
        trafficMap.put("allTimeUniqueVisitors", allTimeVisitors);
        responseData.put("trafficVolume", trafficMap);

        Map<String, Object> engMap = new LinkedHashMap<>();
        engMap.put("averageSessionDurationSeconds", Math.round(avgDuration));
        engMap.put("averageEngagementDisplay", String.format("%dm %02ds", durMin, durSec));
        engMap.put("averageActiveDwellSeconds", Math.round(avgActive));
        engMap.put("bounceRatePercentage", round1(bounce));
        engMap.put("averagePageViewsPerSession", round1(avgPages));
        responseData.put("engagement", engMap);

        responseData.put("topTrafficSources", trafficSources != null ? trafficSources : Collections.emptyList());
        responseData.put("topCampaignAttribution", campaignAttribution != null ? campaignAttribution : Collections.emptyList());
        responseData.put("geographicDistribution", geo != null ? geo : Collections.emptyList());

        Map<String, Object> envMap = new LinkedHashMap<>();
        envMap.put("mobilePercentage", mobilePct);
        envMap.put("desktopPercentage", round1(100.0 - mobilePct));
        envMap.put("inAppBrowserPercentage", inAppPct);
        responseData.put("environment", envMap);

        Map<String, Object> funnelMap = new LinkedHashMap<>();
        funnelMap.put("campaignViews", totalViews);
        funnelMap.put("applyButtonClicks", totalApplies);
        funnelMap.put("overallConversionRate", convRate);
        responseData.put("funnelConversion", funnelMap);

        return ResponseEntity.ok(ApiResponse.ok(responseData));
    }

    // ---------- helpers ----------

    private static double num(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : defaultValue;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String headerOrDefault(HttpServletRequest req, String primary, String secondary, String fallback) {
        String v = req.getHeader(primary);
        if (v == null || v.isBlank()) v = req.getHeader(secondary);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "0.0.0.0";
    }

    private String hashVisitor(String visitorId, String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((visitorId + "_" + ip).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(visitorId.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    private boolean isInAppBrowser(String ua, String referrer) {
        if (ua != null) {
            String lower = ua.toLowerCase();
            if (lower.contains("instagram") || lower.contains("fb_iab")
                    || lower.contains("fban") || lower.contains("whatsapp")) {
                return true;
            }
        }
        return referrer != null && (referrer.contains("instagram.com") || referrer.contains("whatsapp"));
    }

    private String detectDeviceType(String ua) {
        if (ua == null) return "Desktop";
        String lower = ua.toLowerCase();
        if (lower.contains("ipad") || lower.contains("tablet")) return "Tablet";
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) return "Mobile";
        return "Desktop";
    }

    private String detectBrowser(String ua) {
        if (ua == null) return "Other";
        String lower = ua.toLowerCase();
        if (lower.contains("instagram")) return "Instagram In-App";
        if (lower.contains("chrome")) return "Chrome";
        if (lower.contains("safari")) return "Safari";
        if (lower.contains("firefox")) return "Firefox";
        return "Other";
    }
}