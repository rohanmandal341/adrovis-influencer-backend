package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicCampaignResponse>>> getPublicCampaigns(
            @ModelAttribute CampaignFilterRequest filter) {
        Page<PublicCampaignResponse> campaigns = campaignService.getFilteredCampaigns(filter);
        return ResponseEntity.ok(ApiResponse.ok(campaigns));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicCampaignResponse>> getCampaignDetails(@PathVariable UUID id) {
        PublicCampaignResponse campaign = campaignService.getCampaignById(id);
        return ResponseEntity.ok(ApiResponse.ok(campaign));
    }

    /**
     * Application Link Proxy:
     * Logs the click-through / analytics and securely redirects the creator
     * to the actual Google Form without exposing destination links up-front.
     */
    @GetMapping("/{id}/apply")
    public ResponseEntity<Void> applyToCampaign(@PathVariable UUID id) {
        String destinationUrl = campaignService.trackAndGetApplicationUrl(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(destinationUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
