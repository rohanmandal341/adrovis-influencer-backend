package com.adrovis.influencer.campaignmanagement.application.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.PublisherApplicationDetailResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.ReviewCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.application.service.PublisherCampaignApplicationService;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/applications")
@RequiredArgsConstructor
public class PublisherCampaignApplicationController {

    private final PublisherCampaignApplicationService applications;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublisherCampaignApplicationResponse>>> list(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) CampaignApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        applications.list(campaignId, status, page, size)
                ));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<PublisherApplicationDetailResponse>> detail(
            @PathVariable UUID campaignId,
            @PathVariable UUID applicationId
    ) {
        var detail = applications.detail(campaignId, applicationId);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + detail.application().version() + "\"")
                .body(ApiResponse.ok(detail));
    }

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApiResponse<PublisherCampaignApplicationResponse>> review(
            @PathVariable UUID campaignId,
            @PathVariable UUID applicationId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ReviewCampaignApplicationRequest request
    ) {
        var result = applications.review(
                campaignId,
                applicationId,
                parseApplicationVersion(ifMatch),
                request
        );

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + result.version() + "\"")
                .body(ApiResponse.ok(
                        "Application review saved successfully.",
                        result
                ));
    }

    private long parseApplicationVersion(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalidVersion();
        }

        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalidVersion();
        }
    }

    private CampaignManagementException invalidVersion() {
        return new CampaignManagementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERSION_HEADER",
                "If-Match must contain a quoted application version."
        );
    }
}