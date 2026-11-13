package com.adrovis.influencer.campaignmanagement.application.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.CreatorApplicationUpdateResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.CreatorCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.SubmitCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.service.CreatorCampaignApplicationService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CreatorCampaignApplicationController {

    private final CreatorCampaignApplicationService applications;

    @PostMapping("/api/v1/campaigns/managed/{campaignId}/applications")
    public ResponseEntity<ApiResponse<CreatorCampaignApplicationResponse>> submit(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SubmitCampaignApplicationRequest request
    ) {
        var application = applications.submit(campaignId, request);

        return ResponseEntity.created(URI.create(
                        "/api/v1/users/me/campaign-applications/"
                                + application.id()
                ))
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        "Application submitted successfully.",
                        application
                ));
    }

    @GetMapping("/api/v1/users/me/campaign-applications")
    public ResponseEntity<ApiResponse<Page<CreatorCampaignApplicationResponse>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.listMine(page, size)));
    }

    @GetMapping("/api/v1/users/me/campaign-applications/{applicationId}")
    public ResponseEntity<ApiResponse<CreatorCampaignApplicationResponse>> detail(
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.getMine(applicationId)));
    }

    @GetMapping("/api/v1/users/me/campaign-applications/{applicationId}/updates")
    public ResponseEntity<ApiResponse<List<CreatorApplicationUpdateResponse>>> updates(
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.updates(applicationId)));
    }
}