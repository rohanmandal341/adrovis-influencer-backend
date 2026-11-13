package com.adrovis.influencer.campaignmanagement.form.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.ConfigureCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationSettingsService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/application-settings")
@RequiredArgsConstructor
public class CampaignApplicationSettingsController {

    private final CampaignApplicationSettingsService settings;

    /**
     * Requires the campaign version the browser last read.
     * Example header: If-Match: "0"
     */
    @PutMapping
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> configure(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ConfigureCampaignApplicationRequest request
    ) {
        long expectedVersion = parseVersion(ifMatch);

        ManagedCampaignResponse campaign = settings.configure(
                campaignId,
                expectedVersion,
                request
        );

        return ResponseEntity.ok()
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(
                        "Application settings updated successfully.",
                        campaign
                ));
    }

    private long parseVersion(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalidVersion();
        }

        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalidVersion();
        }
    }

    private ResponseStatusException invalidVersion() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "If-Match must contain a quoted non-negative campaign version."
        );
    }
}