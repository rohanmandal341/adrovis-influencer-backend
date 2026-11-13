package com.adrovis.influencer.campaignmanagement.dashboard.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.dashboard.dto.CampaignDashboardOverview;
import com.adrovis.influencer.campaignmanagement.dashboard.service.CampaignDashboardService;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import com.adrovis.influencer.dto.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/dashboard")
@RequiredArgsConstructor
public class CampaignDashboardController {

    private final CampaignDashboardService dashboard;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<CampaignDashboardOverview>> overview() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.overview()));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<Page<ManagedCampaignResponse>>> campaigns(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) PublisherType publisherType,
            @RequestParam(required = false) UUID publisherProfileId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.campaigns(
                        search,
                        status,
                        publisherType,
                        publisherProfileId,
                        includeArchived,
                        page,
                        size
                )));
    }

    @GetMapping("/campaigns/{campaignId}/applications")
    public ResponseEntity<ApiResponse<Page<PublisherCampaignApplicationResponse>>> applicants(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.applicants(
                        campaignId, search, status, page, size
                )));
    }

    @GetMapping("/campaigns/{campaignId}/applications/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignApplicationStatus status
    ) {
        var file = dashboard.export(campaignId, search, status);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        file.filename(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .contentType(new MediaType(
                        "text", "csv", StandardCharsets.UTF_8
                ))
                .contentLength(file.content().length)
                .body(file.content());
    }
}