package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfigureCampaignApplicationRequest(

        @NotNull(message = "Application mode is required.")
        ApplicationMode mode,

        @Size(max = 2048)
        String externalUrl
) {

    public ConfigureCampaignApplicationRequest {
        externalUrl = externalUrl == null || externalUrl.isBlank()
                ? null
                : externalUrl.trim();
    }

    @AssertTrue(message =
            "PLATFORM must not include an external URL. "
                    + "GOOGLE_FORM requires a supported HTTPS Google Forms URL.")
    public boolean isApplicationConfigurationValid() {
        if (mode == null) {
            return true; // @NotNull handles the missing mode.
        }

        return switch (mode) {
            case PLATFORM -> externalUrl == null;
            case GOOGLE_FORM -> GoogleFormUrl.isValid(externalUrl);
        };
    }
}