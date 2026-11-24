package com.adrovis.influencer.campaign.access;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyAccessResponse {
    // READY | MISSING_PROFILE_FIELDS | ONBOARDING_REQUIRED
    private String status;
    private String applicationUrl;
    private Integer prefilledCount;
    private Integer totalMappedCount;
    private Boolean requiresGoogleLogin;
    private List<MissingFieldItem> missingFields;
    private Map<String, String> quickCopyData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingFieldItem {
        private String key;
        private String label;
        private String formQuestionLabel;
        private String reason;
        private String type; // NUMBER | TEXT | TEXTAREA | SELECT
        private String placeholder;
        private String currentValue;
        private List<String> options;
    }
}