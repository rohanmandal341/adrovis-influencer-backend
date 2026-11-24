package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.campaign.access.ApplyAccessResponse.MissingFieldItem;
import com.adrovis.influencer.campaign.access.CreatorPrefillProfileService.CreatorVault;
import com.adrovis.influencer.entity.CampaignEntity;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormPrefillUrlBuilder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*([kKmM])?");

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

        // 1. Detect missing required core profile fields for Learn-As-You-Apply drawer
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

        // 2. Build usp=pp_url query parameters
        List<String> queryPairs = new ArrayList<>();
        queryPairs.add("usp=pp_url");

        int filledCount = 0;
        Map<String, String> scalar = vault.getScalarValues();

        for (Map<String, Object> q : prefillMap) {
            String entryId = asString(q.get("entry_id"));
            String semanticKey = asString(q.get("semantic_key"));
            String fieldType = asString(q.get("field_type"));
            boolean hasOther = Boolean.TRUE.equals(q.get("has_other_option"));
            List<String> options = extractOptions(q.get("options"));

            if (entryId.isBlank() || semanticKey.isBlank()) {
                continue;
            }

            if ("confirm_checkbox".equals(semanticKey) && !options.isEmpty()) {
                queryPairs.add(encodeParam(entryId, options.get(0)));
                filledCount++;
                continue;
            }

            if ("confirm_yes".equals(semanticKey) && !options.isEmpty()) {
                String yesOpt = options.stream()
                        .filter(o -> o.equalsIgnoreCase("Yes") || o.toLowerCase(Locale.ROOT).startsWith("yes"))
                        .findFirst()
                        .orElse(null);
                if (yesOpt != null) {
                    queryPairs.add(encodeParam(entryId, yesOpt));
                    filledCount++;
                }
                continue;
            }

            if ("niches_list".equals(semanticKey) && "CHECKBOX".equals(fieldType) && !options.isEmpty()) {
                List<String> matchedOpts = matchMultipleChoices(vault.getNichesList(), options);
                if (!matchedOpts.isEmpty()) {
                    for (String opt : matchedOpts) {
                        queryPairs.add(encodeParam(entryId, opt));
                    }
                    filledCount++;
                }
                continue;
            }

            if ("followers_count".equals(semanticKey) && !options.isEmpty() && vault.getFollowersCount() != null) {
                String bracket = matchNumericBracket(vault.getFollowersCount(), options);
                if (bracket != null) {
                    queryPairs.add(encodeParam(entryId, bracket));
                    filledCount++;
                }
                continue;
            }

            String rawVal = scalar.get(semanticKey);
            if (rawVal == null || rawVal.isBlank()) {
                continue;
            }

            if (("RADIO".equals(fieldType) || "DROPDOWN".equals(fieldType)) && !options.isEmpty()) {
                String matchedOption = matchSingleChoice(rawVal, options, hasOther);
                if (matchedOption != null) {
                    queryPairs.add(encodeParam(entryId, matchedOption));
                    filledCount++;
                }
            } else if ("CHECKBOX".equals(fieldType) && !options.isEmpty()) {
                String matchedOption = matchSingleChoice(rawVal, options, false);
                if (matchedOption != null) {
                    queryPairs.add(encodeParam(entryId, matchedOption));
                    filledCount++;
                }
            } else {
                queryPairs.add(encodeParam(entryId, rawVal));
                filledCount++;
            }
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
                .quickCopyData(vault.getQuickCopyData())
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
            if (!required || key.isBlank()) continue;

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
            }
        }

        return new ArrayList<>(needed.values());
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
            String uLow = userItem.toLowerCase(Locale.ROOT);
            for (String opt : options) {
                String oLow = opt.toLowerCase(Locale.ROOT);
                if (oLow.equals(uLow) || oLow.contains(uLow) || uLow.contains(oLow)) {
                    matched.add(opt);
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private String matchSingleChoice(String value, List<String> options, boolean hasOther) {
        String vLow = value.trim().toLowerCase(Locale.ROOT);
        for (String opt : options) {
            if (opt.equalsIgnoreCase(value.trim())) return opt;
        }
        for (String opt : options) {
            String oLow = opt.toLowerCase(Locale.ROOT);
            if (oLow.contains(vLow) || vLow.contains(oLow)) return opt;
        }
        if (hasOther) {
            return "__other_option__";
        }
        for (String opt : options) {
            if (opt.equalsIgnoreCase("Other") || opt.equalsIgnoreCase("Others")) return opt;
        }
        return null;
    }

    private List<String> extractOptions(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s -> !s.isBlank()).toList();
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