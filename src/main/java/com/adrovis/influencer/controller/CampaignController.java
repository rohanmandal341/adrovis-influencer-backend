package com.adrovis.influencer.controller;

import com.adrovis.influencer.campaign.access.ApplyAccessResponse;
import com.adrovis.influencer.campaign.access.CampaignApplyAccessService;
import com.adrovis.influencer.campaign.access.GuestApplyCookie;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.service.CampaignService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignApplyAccessService applyAccess;
    private final GuestApplyCookie guestCookies;

    /** Preserves public campaign discovery and the existing filter contract. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicCampaignResponse>>> getPublicCampaigns(
            @ModelAttribute CampaignFilterRequest filter) {
        Page<PublicCampaignResponse> campaigns = campaignService.getFilteredCampaigns(filter);
        return ResponseEntity.ok(ApiResponse.ok(campaigns));
    }

    /** Preserves public campaign details without exposing the direct application destination. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicCampaignResponse>> getCampaignDetails(@PathVariable UUID id) {
        PublicCampaignResponse campaign = campaignService.getCampaignById(id);
        return ResponseEntity.ok(ApiResponse.ok(campaign));
    }

    /**
     * Application Link Proxy:
     * Logs the click-through / analytics and securely redirects the creator
     * to the pre-filled Google Form without exposing destination links up-front.
     */
    @GetMapping("/{id}/apply")
    public ResponseEntity<Void> applyToCampaign(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        ApplyAccessResponse result = grantAccess(id, guest, account, response);
        String destination = (result.getApplicationUrl() != null && !result.getApplicationUrl().isBlank())
                ? result.getApplicationUrl()
                : campaignService.getApplicationDestination(id);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    /** Returns a gated JSON destination (with pre-filled form URL or missing profile fields). */
    @PostMapping("/{id}/apply-access")
    public ResponseEntity<ApiResponse<ApplyAccessResponse>> applyAccess(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        ApplyAccessResponse result = grantAccess(id, guest, account, response);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(result));
    }

    /** Validates the destination and atomically enforces the selected guest policy before recording a click. */
    private ApplyAccessResponse grantAccess(UUID id, String raw, Jwt account, HttpServletResponse response) {
        if (account != null) {
            return applyAccess.grantWithPrefill(id, null, account);
        }
        String identity = guestCookies.identity(raw);
        ApplyAccessResponse result = applyAccess.grantWithPrefill(id, guestCookies.hash(identity), null);
        if (!identity.equals(raw)) {
            guestCookies.write(response, identity);
        }
        return result;
    }
}