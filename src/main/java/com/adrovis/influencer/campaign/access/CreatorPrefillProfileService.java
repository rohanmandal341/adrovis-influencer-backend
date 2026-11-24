package com.adrovis.influencer.campaign.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CreatorPrefillProfileService {

    private static final int MAX_CUSTOM_FIELDS = 100;
    private static final int MAX_CUSTOM_KEY_LENGTH = 64;
    private static final int MAX_CUSTOM_VALUE_LENGTH = 500;

    private final NamedParameterJdbcTemplate securityJdbc;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private volatile String resolvedSchema = "adrovis_security";

    public CreatorPrefillProfileService(
            @Autowired(required = false) @Qualifier("securityJdbcTemplate") NamedParameterJdbcTemplate securityJdbc,
            ObjectMapper objectMapper,
            Environment environment) {
        this.securityJdbc = securityJdbc;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostConstruct
    public void verifySecurityDatasourceOnStartup() {
        String[] activeProfiles = environment.getActiveProfiles();
        String configuredUrl = environment.getProperty("security.datasource.url");
        log.info("[Prefill-Init] Active profiles: {} | security.datasource.url present: {}",
                Arrays.toString(activeProfiles), configuredUrl != null && !configuredUrl.isBlank());

        if (securityJdbc == null) {
            log.error("[Prefill-Init] securityJdbcTemplate is NULL! Configure security.datasource.* in your active profile.");
            return;
        }

        try {
            List<String> schemas = securityJdbc.queryForList(
                    "SELECT table_schema FROM information_schema.tables WHERE table_name = 'users' AND table_schema IN ('adrovis_security', 'public')",
                    Collections.emptyMap(),
                    String.class
            );
            if (schemas.contains("adrovis_security")) {
                resolvedSchema = "adrovis_security";
            } else if (schemas.contains("public")) {
                resolvedSchema = "public";
            }
            Integer userCount = securityJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + resolvedSchema + ".users",
                    Collections.emptyMap(),
                    Integer.class
            );
            log.info("[Prefill-Init] Connected to Security DB! Using schema='{}' (users count={})", resolvedSchema, userCount);
        } catch (Exception e) {
            log.error("[Prefill-Init] Connected to Security DB pool, but failed querying users table: {}", e.getMessage(), e);
        }
    }

    @Data
    @Builder
    public static class CreatorVault {
        private UUID userId;
        private String fullName;
        private String email;
        private String instagramHandle;
        private String city;
        private boolean onboardingComplete;
        private Long followersCount;
        private String followersUpdatedAt;
        private boolean followersStale;
        private Map<String, String> scalarValues;
        private Map<String, String> customFields;
        private List<String> nichesList;
        private Map<String, String> quickCopyData;
    }

    public CreatorPrefillProfileResponse getProfileResponse(Jwt jwt) {
        CreatorVault vault = loadCreatorVault(jwt)
                .orElseThrow(() -> new IllegalArgumentException("Creator account not found in security database."));
        Map<String, String> s = vault.getScalarValues();
        Long ageValue = parseLong(s.get("age"));
        return CreatorPrefillProfileResponse.builder()
                .fullName(vault.getFullName())
                .email(vault.getEmail())
                .phoneNumber(s.getOrDefault("phone_10_digit", ""))
                .gender(s.getOrDefault("gender", ""))
                .city(s.getOrDefault("city", ""))
                .state(s.getOrDefault("state", ""))
                .instagramProfileUrl(s.getOrDefault("instagram_url", ""))
                .followersCount(vault.getFollowersCount())
                .followersUpdatedAt(vault.getFollowersUpdatedAt())
                .shippingAddress(s.getOrDefault("shipping_address", ""))
                .pincode(s.getOrDefault("pincode", ""))
                .youtubeUrl(s.getOrDefault("youtube_url", ""))
                .portfolioUrl(s.getOrDefault("portfolio_url", ""))
                .age(ageValue != null ? ageValue.intValue() : null)
                .profileType(s.getOrDefault("profile_type", ""))
                .preferredLanguage(s.getOrDefault("preferred_language", ""))
                .niches(vault.getNichesList())
                .customFields(vault.getCustomFields())
                .build();
    }

    public Optional<CreatorVault> loadCreatorVault(Jwt jwt) {
        if (securityJdbc == null || jwt == null) {
            return Optional.empty();
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (String claim : List.of("sub", "userId", "user_id", "id", "uid", "email")) {
            Object val = jwt.getClaims().get(claim);
            if (val != null && !String.valueOf(val).isBlank()) {
                candidates.add(String.valueOf(val).trim());
            }
        }

        for (String candidate : candidates) {
            Optional<CreatorVault> found = loadCreatorVault(candidate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public Optional<CreatorVault> loadCreatorVault(String principal) {
        if (securityJdbc == null || principal == null || principal.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT u.id,
                   u.full_name,
                   u.email,
                   u.phone_number,
                   u.gender,
                   u.city,
                   u.state,
                   u.instagram_profile_url,
                   u.onboarding_complete,
                   CAST(rp.profile AS text) AS profile_json
            FROM %s.users u
            LEFT JOIN %s.registration_profiles rp
              ON rp.user_id = u.id AND UPPER(rp.role) = 'CREATOR'
            WHERE CAST(u.id AS text) = :principal OR LOWER(u.email) = LOWER(:principal)
            LIMIT 1
            """.formatted(resolvedSchema, resolvedSchema);

        List<Map<String, Object>> rows;
        try {
            rows = securityJdbc.queryForList(sql, new MapSqlParameterSource("principal", principal.trim()));
        } catch (Exception e) {
            log.error("[Prefill] SQL error querying {}.users for principal='{}': {}", resolvedSchema, principal, e.getMessage(), e);
            return Optional.empty();
        }

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> row = rows.get(0);
        UUID userId = UUID.fromString(String.valueOf(row.get("id")));
        boolean onboardingComplete = Boolean.TRUE.equals(row.get("onboarding_complete"));
        Map<String, Object> profileMap = parseJsonMap(asString(row.get("profile_json")));

        String fullName = asString(row.get("full_name"));
        String email = asString(row.get("email"));
        String rawPhone = asString(row.get("phone_number"));
        String gender = asString(row.get("gender"));
        String city = asString(row.get("city"));
        String state = asString(row.get("state"));
        String rawIgUrl = asString(row.get("instagram_profile_url"));

        String firstName = "";
        String lastName = "";
        if (!fullName.isBlank()) {
            String[] parts = fullName.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        String igUrl = "";
        String igHandle = "";
        if (!rawIgUrl.isBlank()) {
            String cleaned = rawIgUrl.split("\\?")[0].replaceAll("/+$", "");
            int slashIdx = cleaned.lastIndexOf('/');
            igHandle = slashIdx >= 0 ? cleaned.substring(slashIdx + 1) : cleaned;
            igHandle = igHandle.replaceAll("^@+", "");
            igUrl = cleaned.startsWith("http") ? cleaned : "https://instagram.com/" + igHandle;
        }

        String digits = rawPhone.replaceAll("\\D", "");
        String phone10 = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
        String phoneE164 = phone10.length() == 10 ? "+91" + phone10 : rawPhone;

        String cityState = (!city.isBlank() && !state.isBlank())
                ? city + ", " + state
                : (!city.isBlank() ? city : state);

        List<String> niches = extractStringList(profileMap.get("niches"));
        String youtubeUrl = cleanUrl(asString(profileMap.get("youtube")));
        String shippingAddress = asString(profileMap.get("shipping_address"));
        String pincode = asString(profileMap.get("pincode"));
        String portfolioUrl = asString(profileMap.get("portfolio_url"));
        String age = asString(profileMap.get("age"));
        String profileType = asString(profileMap.get("profile_type"));
        String preferredLanguage = asString(profileMap.get("preferred_language"));

        // Extract all arbitrary dynamic custom_fields learned from past forms
        Map<String, String> customFields = new LinkedHashMap<>();
        if (profileMap.get("custom_fields") instanceof Map<?, ?> rawCustom) {
            for (Map.Entry<?, ?> entry : rawCustom.entrySet()) {
                String k = asString(entry.getKey());
                String v = asString(entry.getValue());
                if (!k.isBlank() && !v.isBlank()) {
                    customFields.put(k, v);
                }
            }
        }

        Long followersCount = parseLong(profileMap.get("followers_count"));
        boolean followersStale = false;
        String updatedAtStr = asString(profileMap.get("followers_updated_at"));
        if (followersCount != null && !updatedAtStr.isBlank()) {
            try {
                Instant updatedAt = OffsetDateTime.parse(updatedAtStr).toInstant();
                followersStale = updatedAt.isBefore(Instant.now().minus(60, ChronoUnit.DAYS));
            } catch (Exception ignored) {
                // unparseable timestamp: treat as not stale
            }
        }

        Map<String, String> scalar = new LinkedHashMap<>();
        // Merge customFields first so explicit core fields always take precedence
        scalar.putAll(customFields);

        putIfPresent(scalar, "full_name", fullName);
        putIfPresent(scalar, "first_name", firstName);
        putIfPresent(scalar, "last_name", lastName);
        putIfPresent(scalar, "email", email);
        putIfPresent(scalar, "instagram_url", igUrl);
        putIfPresent(scalar, "instagram_handle", igHandle);
        putIfPresent(scalar, "instagram_handle_at", igHandle.isBlank() ? "" : "@" + igHandle);
        putIfPresent(scalar, "phone_e164", phoneE164);
        putIfPresent(scalar, "phone_10_digit", phone10);
        putIfPresent(scalar, "city", city);
        putIfPresent(scalar, "state", state);
        putIfPresent(scalar, "city_state", cityState);
        putIfPresent(scalar, "gender", gender);
        if (followersCount != null && followersCount > 0) {
            scalar.put("followers_count", String.valueOf(followersCount));
        }
        if (!niches.isEmpty()) {
            scalar.put("niches_csv", String.join(", ", niches));
        }
        putIfPresent(scalar, "youtube_url", youtubeUrl);
        putIfPresent(scalar, "shipping_address", shippingAddress);
        putIfPresent(scalar, "pincode", pincode);
        putIfPresent(scalar, "portfolio_url", portfolioUrl);
        putIfPresent(scalar, "age", age);
        putIfPresent(scalar, "profile_type", profileType);
        putIfPresent(scalar, "preferred_language", preferredLanguage);

        Map<String, String> quickCopy = new LinkedHashMap<>();
        putIfPresent(quickCopy, "Full Name", fullName);
        putIfPresent(quickCopy, "IG Link", igUrl);
        putIfPresent(quickCopy, "IG Handle", igHandle.isBlank() ? "" : "@" + igHandle);
        putIfPresent(quickCopy, "Phone", phone10);
        putIfPresent(quickCopy, "Email", email);
        putIfPresent(quickCopy, "City", cityState);
        if (followersCount != null && followersCount > 0) {
            quickCopy.put("Followers", String.valueOf(followersCount));
        }
        putIfPresent(quickCopy, "Age", age);
        putIfPresent(quickCopy, "Profile Type", profileType);
        putIfPresent(quickCopy, "Language", preferredLanguage);
        putIfPresent(quickCopy, "Address", shippingAddress);
        putIfPresent(quickCopy, "Pincode", pincode);

        return Optional.of(CreatorVault.builder()
                .userId(userId)
                .fullName(fullName)
                .email(email)
                .instagramHandle(igHandle.isBlank() ? "" : "@" + igHandle)
                .city(cityState)
                .onboardingComplete(onboardingComplete)
                .followersCount(followersCount)
                .followersUpdatedAt(updatedAtStr)
                .followersStale(followersStale)
                .scalarValues(scalar)
                .customFields(customFields)
                .nichesList(niches)
                .quickCopyData(quickCopy)
                .build());
    }

    public List<String> updateProfile(Jwt jwt, UpdateCreatorPrefillProfileRequest req) {
        CreatorVault existing = loadCreatorVault(jwt)
                .orElseThrow(() -> new IllegalArgumentException("Creator account not found in security database."));
        return updateProfileById(existing, req);
    }

    private List<String> updateProfileById(CreatorVault existing, UpdateCreatorPrefillProfileRequest req) {
        if (securityJdbc == null) {
            throw new IllegalStateException("Security database connection is not configured.");
        }

        UUID userId = existing.getUserId();
        List<String> updatedFieldKeys = new ArrayList<>();
        MapSqlParameterSource userParams = new MapSqlParameterSource("id", userId);
        List<String> setClauses = new ArrayList<>();

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            String digits = req.getPhoneNumber().replaceAll("\\D", "");
            String normalized = digits.length() == 10 ? "+91" + digits : req.getPhoneNumber().trim();
            setClauses.add("phone_number = :phone");
            userParams.addValue("phone", normalized);
            updatedFieldKeys.add("phoneNumber");
        }
        if (req.getGender() != null && !req.getGender().isBlank()) {
            setClauses.add("gender = :gender");
            userParams.addValue("gender", req.getGender().trim().toUpperCase(Locale.ROOT));
            updatedFieldKeys.add("gender");
        }
        if (req.getCity() != null && !req.getCity().isBlank()) {
            setClauses.add("city = :city");
            userParams.addValue("city", req.getCity().trim());
            updatedFieldKeys.add("city");
        }
        if (req.getState() != null && !req.getState().isBlank()) {
            setClauses.add("state = :state");
            userParams.addValue("state", req.getState().trim());
            updatedFieldKeys.add("state");
        }
        if (req.getInstagramProfileUrl() != null && !req.getInstagramProfileUrl().isBlank()) {
            setClauses.add("instagram_profile_url = :igUrl");
            userParams.addValue("igUrl", req.getInstagramProfileUrl().trim());
            updatedFieldKeys.add("instagramProfileUrl");
        }

        if (!setClauses.isEmpty()) {
            setClauses.add("updated_at = NOW()");
            String updateUserSql = "UPDATE " + resolvedSchema + ".users SET " + String.join(", ", setClauses) + " WHERE id = :id";
            securityJdbc.update(updateUserSql, userParams);
        }

        Map<String, Object> jsonPatch = new LinkedHashMap<>();
        if (req.getFollowersCount() != null && req.getFollowersCount() > 0) {
            jsonPatch.put("followers_count", req.getFollowersCount());
            jsonPatch.put("followers_updated_at", OffsetDateTime.now().toString());
            updatedFieldKeys.add("followersCount");
        }
        if (req.getShippingAddress() != null && !req.getShippingAddress().isBlank()) {
            jsonPatch.put("shipping_address", req.getShippingAddress().trim());
            updatedFieldKeys.add("shippingAddress");
        }
        if (req.getPincode() != null && !req.getPincode().isBlank()) {
            jsonPatch.put("pincode", req.getPincode().trim());
            updatedFieldKeys.add("pincode");
        }
        if (req.getYoutubeUrl() != null && !req.getYoutubeUrl().isBlank()) {
            jsonPatch.put("youtube", cleanUrl(req.getYoutubeUrl()));
            updatedFieldKeys.add("youtubeUrl");
        }
        if (req.getPortfolioUrl() != null && !req.getPortfolioUrl().isBlank()) {
            jsonPatch.put("portfolio_url", req.getPortfolioUrl().trim());
            updatedFieldKeys.add("portfolioUrl");
        }
        if (req.getAge() != null && req.getAge() > 0) {
            jsonPatch.put("age", req.getAge());
            updatedFieldKeys.add("age");
        }
        if (req.getProfileType() != null && !req.getProfileType().isBlank()) {
            jsonPatch.put("profile_type", req.getProfileType().trim());
            updatedFieldKeys.add("profileType");
        }
        if (req.getPreferredLanguage() != null && !req.getPreferredLanguage().isBlank()) {
            jsonPatch.put("preferred_language", req.getPreferredLanguage().trim());
            updatedFieldKeys.add("preferredLanguage");
        }
        if (req.getNiches() != null && !req.getNiches().isEmpty()) {
            jsonPatch.put("niches", req.getNiches());
            updatedFieldKeys.add("niches");
        }

        // Merge any arbitrary dynamic fields learned from forms
        if (req.getCustomFields() != null && !req.getCustomFields().isEmpty()) {
            Map<String, String> mergedCustom = new LinkedHashMap<>();
            if (existing.getCustomFields() != null) {
                mergedCustom.putAll(existing.getCustomFields());
            }
            boolean changed = false;
            for (Map.Entry<String, Object> entry : req.getCustomFields().entrySet()) {
                String cleanKey = normalizeCustomKey(entry.getKey());
                String cleanVal = asString(entry.getValue());
                if (cleanKey.isBlank() || cleanVal.isBlank()) {
                    continue;
                }
                if (cleanVal.length() > MAX_CUSTOM_VALUE_LENGTH) {
                    cleanVal = cleanVal.substring(0, MAX_CUSTOM_VALUE_LENGTH);
                }
                if (!mergedCustom.containsKey(cleanKey) && mergedCustom.size() >= MAX_CUSTOM_FIELDS) {
                    continue;
                }
                mergedCustom.put(cleanKey, cleanVal);
                updatedFieldKeys.add(cleanKey);
                changed = true;
            }
            if (changed) {
                jsonPatch.put("custom_fields", mergedCustom);
            }
        }

        if (!jsonPatch.isEmpty()) {
            try {
                String patchStr = objectMapper.writeValueAsString(jsonPatch);
                MapSqlParameterSource rpParams = new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("patchJson", patchStr);

                String updateRpSql = """
                    UPDATE %s.registration_profiles
                    SET profile = COALESCE(profile, '{}'::jsonb) || CAST(:patchJson AS jsonb),
                        updated_at = NOW()
                    WHERE user_id = :userId AND UPPER(role) = 'CREATOR'
                    """.formatted(resolvedSchema);

                int updatedRows = securityJdbc.update(updateRpSql, rpParams);
                if (updatedRows == 0) {
                    String insertRpSql = """
                        INSERT INTO %s.registration_profiles (user_id, role, profile, created_at, updated_at)
                        VALUES (:userId, 'CREATOR', CAST(:patchJson AS jsonb), NOW(), NOW())
                        """.formatted(resolvedSchema);
                    securityJdbc.update(insertRpSql, rpParams);
                }
            } catch (Exception e) {
                log.error("[Prefill-Update] Failed to merge registration_profiles JSONB for user {}", userId, e);
                throw new RuntimeException("Failed to save creator auto-fill profile.");
            }
        }

        return updatedFieldKeys;
    }

    private String normalizeCustomKey(String rawKey) {
        String key = asString(rawKey).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (key.length() > MAX_CUSTOM_KEY_LENGTH) {
            key = key.substring(0, MAX_CUSTOM_KEY_LENGTH);
        }
        return key;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<String> extractStringList(Object val) {
        if (!(val instanceof List<?> list)) return Collections.emptyList();
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String cleanUrl(String url) {
        if (url == null || url.isBlank()) return "";
        int qIdx = url.indexOf('?');
        return qIdx > 0 ? url.substring(0, qIdx).trim() : url.trim();
    }

    private void putIfPresent(Map<String, String> map, String key, String val) {
        if (val != null && !val.isBlank()) {
            map.put(key, val.trim());
        }
    }

    private String asString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            String digitsOnly = String.valueOf(o).replaceAll("[^0-9]", "");
            return digitsOnly.isEmpty() ? null : Long.parseLong(digitsOnly);
        } catch (Exception e) {
            return null;
        }
    }
}