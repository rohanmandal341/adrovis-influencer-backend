package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.campaign.access.CreatorPrefillProfileService.CreatorVault;
import com.adrovis.influencer.entity.CampaignEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoFillAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void ensureAnalyticsTableExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS public.auto_fill_usage_logs (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    creator_name VARCHAR(255),
                    creator_email VARCHAR(255),
                    instagram_handle VARCHAR(255),
                    city VARCHAR(255),
                    campaign_id UUID,
                    brand_name VARCHAR(255),
                    campaign_title VARCHAR(500),
                    event_type VARCHAR(50) NOT NULL,
                    prefilled_count INT DEFAULT 0,
                    total_mapped_count INT DEFAULT 0,
                    fields_snapshot JSONB DEFAULT '[]'::jsonb,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_autofill_logs_user_id ON public.auto_fill_usage_logs(user_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_autofill_logs_created_at ON public.auto_fill_usage_logs(created_at DESC)");
        } catch (Exception e) {
            log.warn("[AutoFillAnalytics] Table init check skipped: {}", e.getMessage());
        }
    }

    public void recordApplyWithAutoFill(CreatorVault vault, CampaignEntity campaign, int prefilledCount, int totalMappedCount) {
        if (vault == null || campaign == null) return;
        try {
            List<String> filledKeys = new ArrayList<>(vault.getQuickCopyData().keySet());
            String snapshotJson = objectMapper.writeValueAsString(filledKeys);

            jdbcTemplate.update("""
                INSERT INTO public.auto_fill_usage_logs
                    (id, user_id, creator_name, creator_email, instagram_handle, city,
                     campaign_id, brand_name, campaign_title, event_type,
                     prefilled_count, total_mapped_count, fields_snapshot, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPLIED_WITH_AUTOFILL', ?, ?, CAST(? AS jsonb), NOW())
                """,
                    UUID.randomUUID(),
                    vault.getUserId(),
                    vault.getFullName(),
                    vault.getEmail(),
                    vault.getInstagramHandle(),
                    vault.getCity(),
                    campaign.getId(),
                    campaign.getBrandName(),
                    campaign.getCampaignTitle(),
                    prefilledCount,
                    totalMappedCount,
                    snapshotJson
            );
        } catch (Exception e) {
            log.warn("[AutoFillAnalytics] Failed to record apply event: {}", e.getMessage());
        }
    }

    public void recordProgressiveFieldsSaved(CreatorVault vault, List<String> updatedFieldKeys) {
        if (vault == null || updatedFieldKeys == null || updatedFieldKeys.isEmpty()) return;
        try {
            String snapshotJson = objectMapper.writeValueAsString(updatedFieldKeys);
            jdbcTemplate.update("""
                INSERT INTO public.auto_fill_usage_logs
                    (id, user_id, creator_name, creator_email, instagram_handle, city,
                     event_type, prefilled_count, total_mapped_count, fields_snapshot, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SAVED_PROGRESSIVE_FIELDS', 0, 0, CAST(? AS jsonb), NOW())
                """,
                    UUID.randomUUID(),
                    vault.getUserId(),
                    vault.getFullName(),
                    vault.getEmail(),
                    vault.getInstagramHandle(),
                    vault.getCity(),
                    snapshotJson
            );
        } catch (Exception e) {
            log.warn("[AutoFillAnalytics] Failed to record progressive field save: {}", e.getMessage());
        }
    }

    public Map<String, Object> getAdminAnalyticsSummary() {
        Integer totalAutoFillApplies = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.auto_fill_usage_logs WHERE event_type = 'APPLIED_WITH_AUTOFILL'",
                Integer.class
        );
        Integer uniqueCreators = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM public.auto_fill_usage_logs",
                Integer.class
        );
        Integer progressiveSavesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.auto_fill_usage_logs WHERE event_type = 'SAVED_PROGRESSIVE_FIELDS'",
                Integer.class
        );
        Double avgCoveragePct = jdbcTemplate.queryForObject("""
                SELECT COALESCE(ROUND(AVG(CASE WHEN total_mapped_count > 0 THEN (prefilled_count::numeric / total_mapped_count) * 100 ELSE 0 END), 1), 0)
                FROM public.auto_fill_usage_logs
                WHERE event_type = 'APPLIED_WITH_AUTOFILL' AND total_mapped_count > 0
                """, Double.class);

        // Per-creator adoption breakdown
        List<Map<String, Object>> creatorRows = jdbcTemplate.queryForList("""
                SELECT user_id,
                       MAX(creator_name) AS creator_name,
                       MAX(creator_email) AS creator_email,
                       MAX(instagram_handle) AS instagram_handle,
                       MAX(city) AS city,
                       COUNT(*) FILTER (WHERE event_type = 'APPLIED_WITH_AUTOFILL') AS apply_count,
                       COUNT(*) FILTER (WHERE event_type = 'SAVED_PROGRESSIVE_FIELDS') AS profile_updates_count,
                       COALESCE(SUM(prefilled_count) FILTER (WHERE event_type = 'APPLIED_WITH_AUTOFILL'), 0) AS total_fields_autofilled,
                       COALESCE(SUM(total_mapped_count) FILTER (WHERE event_type = 'APPLIED_WITH_AUTOFILL'), 0) AS total_fields_possible,
                       MAX(created_at) AS last_used_at
                FROM public.auto_fill_usage_logs
                GROUP BY user_id
                ORDER BY last_used_at DESC
                LIMIT 100
                """);

        // Recent activity feed (shows exact campaign + X/Y fields prefilled or which fields were saved)
        List<Map<String, Object>> recentLogs = jdbcTemplate.queryForList("""
                SELECT id,
                       user_id,
                       creator_name,
                       creator_email,
                       instagram_handle,
                       city,
                       campaign_id,
                       brand_name,
                       campaign_title,
                       event_type,
                       prefilled_count,
                       total_mapped_count,
                       CAST(fields_snapshot AS text) AS fields_snapshot_json,
                       created_at
                FROM public.auto_fill_usage_logs
                ORDER BY created_at DESC
                LIMIT 50
                """);

        List<Map<String, Object>> normalizedLogs = new ArrayList<>();
        for (Map<String, Object> row : recentLogs) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String jsonStr = (String) row.get("fields_snapshot_json");
            try {
                item.put("fields_snapshot", jsonStr != null ? objectMapper.readValue(jsonStr, new TypeReference<List<String>>() {}) : List.of());
            } catch (Exception e) {
                item.put("fields_snapshot", List.of());
            }
            item.remove("fields_snapshot_json");
            if (row.get("created_at") instanceof Timestamp ts) {
                item.put("created_at", ts.toInstant().toString());
            }
            normalizedLogs.add(item);
        }

        List<Map<String, Object>> normalizedCreators = new ArrayList<>();
        for (Map<String, Object> row : creatorRows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            if (row.get("last_used_at") instanceof Timestamp ts) {
                item.put("last_used_at", ts.toInstant().toString());
            }
            normalizedCreators.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAutoFillApplies", totalAutoFillApplies != null ? totalAutoFillApplies : 0);
        result.put("uniqueCreators", uniqueCreators != null ? uniqueCreators : 0);
        result.put("progressiveSavesCount", progressiveSavesCount != null ? progressiveSavesCount : 0);
        result.put("averageCoveragePercent", avgCoveragePct != null ? avgCoveragePct : 0.0);
        result.put("creators", normalizedCreators);
        result.put("recentLogs", normalizedLogs);
        return result;
    }
}