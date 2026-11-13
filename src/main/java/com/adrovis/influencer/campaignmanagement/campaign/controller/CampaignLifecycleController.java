package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.CampaignLifecycleService;
import com.adrovis.influencer.campaignmanagement.campaign.service.CampaignLifecycleService.Action;
import com.adrovis.influencer.dto.ApiResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class CampaignLifecycleController {

    private final CampaignLifecycleService lifecycle;

    @PostMapping("/{campaignId}/{action:publish|pause|resume|close}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> transition(
            @PathVariable UUID campaignId,
            @PathVariable String action,
            @RequestHeader("If-Match") String ifMatch
    ) {
        Action requestedAction = Action.valueOf(
                action.toUpperCase(Locale.ROOT)
        );

        ManagedCampaignResponse campaign = lifecycle.transition(
                campaignId,
                CampaignVersionHeader.parse(ifMatch),
                requestedAction
        );

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(
                        "Campaign status updated successfully.",
                        campaign
                ));
    }
}