package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.campaign.access.ApplyAccessResponse.MissingFieldItem;
import com.adrovis.influencer.campaign.access.CreatorPrefillProfileService.CreatorVault;
import com.adrovis.influencer.entity.CampaignEntity;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormPrefillUrlBuilder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*([kKmM])?");
    private static final String OTHER_SENTINEL = "__other_option__";

    // Keys that are auto-derived from existing account data or auto-confirmed without asking the user
    private static final Set<String> AUTO_DERIVED_KEYS = Set.of(
            "full_name", "first_name", "last_name", "email",
            "instagram_url", "instagram_handle", "instagram_handle_at",
            "niches_list", "niches_csv", "confirm_checkbox", "confirm_yes"
    );

    @SuppressWarnings("unchecked")
    public ApplyAccessResponse evaluateAndBuild(CampaignEntity entity, String rawDestinationUrl, CreatorVault vault) {
        Map<String, Object> data = entity.getCanonicalData() != null ? entity.getCanonicalData() : Collections.emptyMap();
        String baseFormUrl = asString(data.get("canonical_form_url"));
        if (baseFormUrl.isBlank()) {
            baseFormUrl = rawDestinationUrl;
        }
        boolean requiresLogin = Boolean.TRUE.equals(data.get("requires_google_login"));

        Object rawMapObj = data.get("form_prefill_map");
        if (!(rawMapObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ApplyAccessResponse.builder()
                    .status("READY")
                    .applicationUrl(rawDestinationUrl)
                    .prefilledCount(0)
                    .totalMappedCount(0)
                    .requiresGoogleLogin(requiresLogin)
                    .missingFields(List.of())
                    .quickCopyData(vault.getQuickCopyData())
                    .build();
        }

        List<Map<String, Object>> prefillMap = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                prefillMap.add((Map<String, Object>) m);
            }
        }

        int totalMapped = (int) prefillMap.stream()
                .filter(q -> !asString(q.get("entry_id")).isBlank() && !asString(q.get("semantic_key")).isBlank())
                .count();

        // 1. Dynamically detect ANY missing required field (built-in OR novel AI-discovered field)
        List<MissingFieldItem> missingFields = detectMissingRequiredFields(prefillMap, vault);
        if (!missingFields.isEmpty()) {
            return ApplyAccessResponse.builder()
                    .status("MISSING_PROFILE_FIELDS")
                    .prefilledCount(Math.max(0, totalMapped - missingFields.size()))
                    .totalMappedCount(totalMapped)
                    .missingFields(missingFields)
                    .requiresGoogleLogin(requiresLogin)
                    .quickCopyData(vault.getQuickCopyData())
                    .build();
        }

        // 2. Build usp=pp_url query parameters + exact showcase map of what was filled on THIS form
        List<String> queryPairs = new ArrayList<>();
        queryPairs.add("usp=pp_url");

        int filledCount = 0;
        Map<String, String> scalar = vault.getScalarValues();
        Map<String, String> filledShowcase = new LinkedHashMap<>();

        for (Map<String, Object> q : prefillMap) {
            String entryId = asString(q.get("entry_id"));
            String semanticKey = asString(q.get("semantic_key"));
            String fieldType = asString(q.get("field_type"));
            String confirmOption = asString(q.get("confirm_option"));
            String promptLabel = asString(q.get("prompt_label"));
            String rawLabel = asString(q.get("label"));
            String displayLabel = !promptLabel.isBlank() ? promptLabel : (!rawLabel.isBlank() ? rawLabel : humanizeKey(semanticKey));
            boolean hasOther = Boolean.TRUE.equals(q.get("has_other_option"));
            List<String> options = extractOptions(q.get("options"));

            if (entryId.isBlank() || semanticKey.isBlank()) {
                continue;
            }

            if ("confirm_checkbox".equals(semanticKey) && !options.isEmpty()) {
                String chosen = !confirmOption.isBlank() ? matchSingleChoice(confirmOption, options, false) : null;
                if (chosen == null) {
                    chosen = options.get(0);
                }
                queryPairs.add(encodeParam(entryId, chosen));
                filledShowcase.putIfAbsent(displayLabel, chosen);
                filledCount++;
                continue;
            }

            if ("confirm_yes".equals(semanticKey)) {
                if (options.isEmpty()) {
                    String val = !confirmOption.isBlank() ? confirmOption : "Yes";
                    queryPairs.add(encodeParam(entryId, val));
                    filledShowcase.putIfAbsent(displayLabel, val);
                    filledCount++;
                } else {
                    String yesOpt = !confirmOption.isBlank()
                            ? matchSingleChoice(confirmOption, options, false)
                            : pickPositiveOption(options);
                    if (yesOpt != null) {
                        queryPairs.add(encodeParam(entryId, yesOpt));
                        filledShowcase.putIfAbsent(displayLabel, yesOpt);
                        filledCount++;
                    }
                }
                continue;
            }

            if ("niches_list".equals(semanticKey) && !options.isEmpty()) {
                List<String> matchedOpts = matchMultipleChoices(vault.getNichesList(), options);
                if (!matchedOpts.isEmpty()) {
                    if ("CHECKBOX".equals(fieldType)) {
                        for (String opt : matchedOpts) {
                            queryPairs.add(encodeParam(entryId, opt));
                        }
                        filledShowcase.putIfAbsent(displayLabel, String.join(", ", matchedOpts));
                    } else {
                        queryPairs.add(encodeParam(entryId, matchedOpts.get(0)));
                        filledShowcase.putIfAbsent(displayLabel, matchedOpts.get(0));
                    }
                    filledCount++;
                }
                continue;
            }

            if ("followers_count".equals(semanticKey) && !options.isEmpty() && vault.getFollowersCount() != null) {
                String bracket = matchNumericBracket(vault.getFollowersCount(), options);
                if (bracket != null) {
                    queryPairs.add(encodeParam(entryId, bracket));
                    filledShowcase.putIfAbsent(displayLabel, bracket);
                    filledCount++;
                }
                continue;
            }

            String rawVal = scalar.get(semanticKey);
            if (rawVal == null || rawVal.isBlank()) {
                continue;
            }

            if (("RADIO".equals(fieldType) || "DROPDOWN".equals(fieldType) || "CHECKBOX".equals(fieldType))
                    && !options.isEmpty()) {
                boolean allowOther = !"CHECKBOX".equals(fieldType) && hasOther;
                String matchedOption = matchSingleChoice(rawVal, options, allowOther);
                if (matchedOption != null) {
                    addChoiceParams(queryPairs, entryId, matchedOption, rawVal);
                    filledShowcase.putIfAbsent(displayLabel, OTHER_SENTINEL.equals(matchedOption) ? rawVal : matchedOption);
                    filledCount++;
                }
            } else {
                queryPairs.add(encodeParam(entryId, rawVal));
                filledShowcase.putIfAbsent(displayLabel, rawVal);
                filledCount++;
            }
        }

        if (filledShowcase.isEmpty()) {
            filledShowcase.putAll(vault.getQuickCopyData());
        }

        String finalUrl = filledCount > 0
                ? baseFormUrl + (baseFormUrl.contains("?") ? "&" : "?") + String.join("&", queryPairs)
                : rawDestinationUrl;

        return ApplyAccessResponse.builder()
                .status("READY")
                .applicationUrl(finalUrl)
                .prefilledCount(filledCount)
                .totalMappedCount(totalMapped)
                .requiresGoogleLogin(requiresLogin)
                .missingFields(List.of())
                .quickCopyData(filledShowcase)
                .build();
    }

    private List<MissingFieldItem> detectMissingRequiredFields(
            List<Map<String, Object>> prefillMap, CreatorVault vault) {
        Map<String, MissingFieldItem> needed = new LinkedHashMap<>();
        Map<String, String> scalar = vault.getScalarValues();

        for (Map<String, Object> q : prefillMap) {
            boolean required = Boolean.TRUE.equals(q.get("required"));
            String key = asString(q.get("semantic_key"));
            String questionLabel = asString(q.get("label"));
            String promptLabel = asString(q.get("prompt_label"));
            String promptReason = asString(q.get("prompt_reason"));
            String inputType = asString(q.get("input_type"));
            List<String> options = extractOptions(q.get("options"));

            if (!required || key.isBlank() || AUTO_DERIVED_KEYS.contains(key)) continue;

            if ("followers_count".equals(key)) {
                if (vault.getFollowersCount() == null || vault.getFollowersCount() <= 0 || vault.isFollowersStale()) {
                    needed.putIfAbsent("followersCount", MissingFieldItem.builder()
                            .key("followersCount")
                            .label(vault.isFollowersStale() ? "Confirm Instagram Followers" : "Instagram Followers")
                            .formQuestionLabel(questionLabel)
                            .reason("Used to automatically select the right follower tier & payout bracket.")
                            .type("NUMBER")
                            .placeholder("e.g. 14500")
                            .currentValue(vault.getFollowersCount() != null ? String.valueOf(vault.getFollowersCount()) : "")
                            .build());
                }
            } else if ("shipping_address".equals(key) && isBlank(scalar.get("shipping_address"))) {
                needed.putIfAbsent("shippingAddress", MissingFieldItem.builder()
                        .key("shippingAddress")
                        .label("Product Delivery Address")
                        .formQuestionLabel(questionLabel)
                        .reason("Required by the brand to dispatch barter/campaign products to you.")
                        .type("TEXTAREA")
                        .placeholder("Flat / House No., Street, Area, Landmark")
                        .build());
            } else if ("pincode".equals(key) && isBlank(scalar.get("pincode"))) {
                needed.putIfAbsent("pincode", MissingFieldItem.builder()
                        .key("pincode")
                        .label("Delivery Pincode")
                        .formQuestionLabel(questionLabel)
                        .reason("Needed for courier serviceability and product dispatch.")
                        .type("TEXT")
                        .placeholder("e.g. 400706")
                        .build());
            } else if ("gender".equals(key) && isBlank(scalar.get("gender"))) {
                needed.putIfAbsent("gender", MissingFieldItem.builder()
                        .key("gender")
                        .label("Gender")
                        .formQuestionLabel(questionLabel)
                        .reason("Required by the campaign form for creator demographic matching.")
                        .type("SELECT")
                        .options(List.of("FEMALE", "MALE", "NON_BINARY"))
                        .build());
            } else if (("phone_10_digit".equals(key) || "phone_e164".equals(key)) && isBlank(scalar.get("phone_10_digit"))) {
                needed.putIfAbsent("phoneNumber", MissingFieldItem.builder()
                        .key("phoneNumber")
                        .label("WhatsApp / Mobile Number")
                        .formQuestionLabel(questionLabel)
                        .reason("Used by brand managers to share campaign briefs and approvals.")
                        .type("TEXT")
                        .placeholder("10-digit mobile number")
                        .build());
            } else if (("city".equals(key) || "city_state".equals(key)) && isBlank(scalar.get("city"))) {
                needed.putIfAbsent("city", MissingFieldItem.builder()
                        .key("city")
                        .label("Current City")
                        .formQuestionLabel(questionLabel)
                        .reason("Used for location eligibility and shipping region matching.")
                        .type("TEXT")
                        .placeholder("e.g. Mumbai")
                        .build());
            } else if ("state".equals(key) && isBlank(scalar.get("state"))) {
                needed.putIfAbsent("state", MissingFieldItem.builder()
                        .key("state")
                        .label("State")
                        .formQuestionLabel(questionLabel)
                        .reason("Used for location eligibility and shipping region matching.")
                        .type("TEXT")
                        .placeholder("e.g. Maharashtra")
                        .build());
            } else if ("youtube_url".equals(key) && isBlank(scalar.get("youtube_url"))) {
                needed.putIfAbsent("youtubeUrl", MissingFieldItem.builder()
                        .key("youtubeUrl")
                        .label(!promptLabel.isBlank() ? promptLabel : "YouTube Channel Link")
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Lets the brand review your YouTube channel.")
                        .type("TEXT")
                        .placeholder("https://youtube.com/@yourchannel")
                        .build());
            } else if ("portfolio_url".equals(key) && isBlank(scalar.get("portfolio_url"))) {
                needed.putIfAbsent("portfolioUrl", MissingFieldItem.builder()
                        .key("portfolioUrl")
                        .label(!promptLabel.isBlank() ? promptLabel : "Portfolio / Work Link")
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Lets the brand review samples of your previous work.")
                        .type("TEXT")
                        .placeholder("Drive / Instagram / website link")
                        .build());
            } else if ("age".equals(key) && isBlank(scalar.get("age"))) {
                needed.putIfAbsent("age", MissingFieldItem.builder()
                        .key("age")
                        .label(!promptLabel.isBlank() ? promptLabel : "Your Age")
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Required by the brand for age eligibility.")
                        .type("NUMBER")
                        .placeholder("e.g. 23")
                        .build());
            } else if ("profile_type".equals(key) && isBlank(scalar.get("profile_type"))) {
                needed.putIfAbsent("profileType", MissingFieldItem.builder()
                        .key("profileType")
                        .label(!promptLabel.isBlank() ? promptLabel : "Creator Profile Type")
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Helps brands match on-camera vs. faceless creators.")
                        .type("SELECT")
                        .options(!options.isEmpty() ? options : List.of("Face Creator", "Faceless Creator"))
                        .build());
            } else if ("preferred_language".equals(key) && isBlank(scalar.get("preferred_language"))) {
                needed.putIfAbsent("preferredLanguage", MissingFieldItem.builder()
                        .key("preferredLanguage")
                        .label(!promptLabel.isBlank() ? promptLabel : "Preferred Content Language")
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Tells the brand which language you create content in.")
                        .type(!options.isEmpty() ? "SELECT" : "TEXT")
                        .options(options)
                        .placeholder("e.g. Hindi, English")
                        .build());
            } else if (isBlank(scalar.get(key))) {
                // 100% DYNAMIC CATCH-ALL: Any novel semantic_key (mother_name, skin_type, upi_id, etc.)
                String dynamicRequestKey = "custom:" + key;
                String resolvedType = !options.isEmpty()
                        ? "SELECT"
                        : ("NUMBER".equals(inputType) || "TEXTAREA".equals(inputType) ? inputType : "TEXT");
                String shownLabel = !promptLabel.isBlank() ? promptLabel : humanizeKey(key);

                needed.putIfAbsent(dynamicRequestKey, MissingFieldItem.builder()
                        .key(dynamicRequestKey)
                        .label(shownLabel)
                        .formQuestionLabel(questionLabel)
                        .reason(!promptReason.isBlank() ? promptReason : "Saved once to your vault and auto-filled on future forms.")
                        .type(resolvedType)
                        .options(options)
                        .placeholder("Enter " + shownLabel.toLowerCase(Locale.ROOT))
                        .build());
            }
        }

        return new ArrayList<>(needed.values());
    }

    private void addChoiceParams(List<String> queryPairs, String entryId, String matchedOption, String rawValue) {
        if (OTHER_SENTINEL.equals(matchedOption)) {
            queryPairs.add(encodeParam(entryId, OTHER_SENTINEL));
            queryPairs.add(encodeParam(entryId + ".other_option_response", rawValue));
        } else {
            queryPairs.add(encodeParam(entryId, matchedOption));
        }
    }

    private String pickPositiveOption(List<String> options) {
        for (String o : options) {
            String l = o.trim().toLowerCase(Locale.ROOT);
            if (l.equals("yes") || l.startsWith("yes") || l.equals("interested")
                    || l.startsWith("i agree") || l.startsWith("agree") || l.startsWith("i confirm")
                    || l.startsWith("i understand") || l.startsWith("already joined")
                    || l.startsWith("joined") || l.startsWith("haan") || l.startsWith("sure")
                    || l.startsWith("accept")) {
                return o;
            }
        }
        return null;
    }

    private String humanizeKey(String snakeKey) {
        String[] parts = snakeKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private String matchNumericBracket(long followers, List<String> options) {
        for (String opt : options) {
            List<Long> bounds = extractNumbers(opt);
            if (bounds.size() >= 2) {
                long low = Math.min(bounds.get(0), bounds.get(1));
                long high = Math.max(bounds.get(0), bounds.get(1));
                if (followers >= low && followers <= high) {
                    return opt;
                }
            } else if (bounds.size() == 1) {
                long bound = bounds.get(0);
                String lowOpt = opt.toLowerCase(Locale.ROOT);
                if ((lowOpt.contains("+") || lowOpt.contains("above") || lowOpt.contains("more")) && followers >= bound) {
                    return opt;
                }
                if ((lowOpt.contains("under") || lowOpt.contains("below") || lowOpt.contains("less") || lowOpt.contains("<")) && followers <= bound) {
                    return opt;
                }
            }
        }
        return null;
    }

    private List<Long> extractNumbers(String text) {
        List<Long> nums = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            double val = Double.parseDouble(m.group(1).replace(",", ""));
            String suffix = m.group(2);
            if ("k".equalsIgnoreCase(suffix)) val *= 1_000;
            else if ("m".equalsIgnoreCase(suffix)) val *= 1_000_000;
            nums.add(Math.round(val));
        }
        return nums;
    }

    private List<String> matchMultipleChoices(List<String> userItems, List<String> options) {
        if (userItems == null || userItems.isEmpty()) return List.of();
        Set<String> matched = new LinkedHashSet<>();
        for (String userItem : userItems) {
            String uTokens = tokens(userItem);
            if (uTokens.isBlank()) continue;
            for (String opt : options) {
                String oTokens = tokens(opt);
                if (oTokens.isBlank()) continue;
                if (oTokens.equals(uTokens) || oTokens.contains(uTokens) || uTokens.contains(oTokens)) {
                    matched.add(opt);
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private String matchSingleChoice(String value, List<String> options, boolean hasOther) {
        if (value == null || value.isBlank()) return null;

        String trimmed = value.trim();
        for (String opt : options) {
            if (opt.equalsIgnoreCase(trimmed)) return opt;
        }

        String vTokens = tokens(trimmed);
        if (!vTokens.isBlank()) {
            for (String opt : options) {
                if (tokens(opt).equals(vTokens)) return opt;
            }
            String best = null;
            int bestLen = -1;
            for (String opt : options) {
                String oTokens = tokens(opt);
                if (oTokens.isBlank()) continue;
                if (oTokens.contains(vTokens) || vTokens.contains(oTokens)) {
                    if (oTokens.length() > bestLen) {
                        best = opt;
                        bestLen = oTokens.length();
                    }
                }
            }
            if (best != null) return best;
        }

        if (hasOther) {
            return OTHER_SENTINEL;
        }
        for (String opt : options) {
            if (opt.equalsIgnoreCase("Other") || opt.equalsIgnoreCase("Others")) return opt;
        }
        return null;
    }

    private String tokens(String text) {
        if (text == null) return "";
        String words = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return words.isEmpty() ? "" : " " + words + " ";
    }

    private List<String> extractOptions(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String encodeParam(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String asString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}