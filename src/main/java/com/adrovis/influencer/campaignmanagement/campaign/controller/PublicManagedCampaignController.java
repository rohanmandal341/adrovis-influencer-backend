package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.PublicManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.PublicManagedCampaignService;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.dto.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/managed")
@RequiredArgsConstructor
public class PublicManagedCampaignController {

    private final PublicManagedCampaignService campaigns;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicManagedCampaignResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(campaigns.list(page, size)));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<PublicManagedCampaignResponse>> detail(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(campaigns.getById(campaignId)));
    }

    @GetMapping("/{campaignId}/application-form")
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> form(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        campaigns.getPublishedForm(campaignId)
                ));
    }
}