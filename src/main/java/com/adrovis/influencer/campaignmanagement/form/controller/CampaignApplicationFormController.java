package com.adrovis.influencer.campaignmanagement.form.controller;

import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationFormService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/application-form")
@RequiredArgsConstructor
public class CampaignApplicationFormController {

    private final CampaignApplicationFormService forms;

    /**
     * Creates a new immutable form version for an authorized draft campaign.
     * Nested field definitions are validated before reaching the service.
     */
    @PostMapping("/versions")
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> createVersion(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CreateApplicationFormVersionRequest request
    ) {
        ApplicationFormVersionResponse version =
                forms.createVersion(campaignId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Application form version created successfully.",
                        version
                ));
    }

    /**
     * Returns the latest management form definition.
     * The service verifies campaign ownership or admin authority.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> latest(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(forms.getLatest(campaignId))
        );
    }
}