package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CreateManagedCampaignRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.ManagedCampaignService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class ManagedCampaignController {

    private final ManagedCampaignService campaigns;

    /**
     * Creates a draft under an authorized publisher profile.
     * Ownership and role checks are enforced by the service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> create(
            @Valid @RequestBody CreateManagedCampaignRequest request
    ) {
        ManagedCampaignResponse campaign = campaigns.create(request);

        URI location = URI.create(
                "/api/v1/portal/campaigns/" + campaign.id()
        );

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(
                        "Campaign draft created successfully.",
                        campaign
                ));
    }

    /**
     * Admin receives all non-archived campaigns.
     * Brand and agency accounts receive only their own campaigns.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ManagedCampaignResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(campaigns.list(page, size))
        );
    }

    /**
     * Returns management details only to the campaign owner or an admin.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(campaigns.getById(id))
        );
    }
}