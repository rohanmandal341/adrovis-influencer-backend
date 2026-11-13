package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.dto.UpdateCampaignDraftRequest;
import com.adrovis.influencer.campaignmanagement.campaign.service.ManagedCampaignEditService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class ManagedCampaignEditController {

    private final ManagedCampaignEditService campaigns;

    @PutMapping("/{campaignId}/draft")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> updateDraft(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateCampaignDraftRequest request
    ) {
        return response(
                "Campaign draft updated successfully.",
                campaigns.updateDraft(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch),
                        request
                )
        );
    }

    @PutMapping("/{campaignId}/details")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> updateDetails(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody CampaignDetailsRequest request
    ) {
        return response(
                "Campaign details updated successfully.",
                campaigns.updateDetails(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch),
                        request
                )
        );
    }

    @DeleteMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> archive(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        return response(
                "Campaign archived successfully.",
                campaigns.archive(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch)
                )
        );
    }

    private ResponseEntity<ApiResponse<ManagedCampaignResponse>> response(
            String message,
            ManagedCampaignResponse campaign
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(message, campaign));
    }
}