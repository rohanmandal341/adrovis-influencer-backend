package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.service.CampaignService;
import com.adrovis.influencer.campaign.access.CampaignApplyAccessService;
import com.adrovis.influencer.campaign.access.GuestApplyCookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
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
    private final CampaignApplyAccessService applyAccess;
    private final GuestApplyCookie guestCookies;

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
    public ResponseEntity<Void> applyToCampaign(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        String destination = grant(id, guest, account, response);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    // JSON alternative lets the frontend display signup before navigating to an external form.
    @PostMapping("/{id}/apply-access")
    public ResponseEntity<Map<String, Object>> applyAccess(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        String destination = grant(id, guest, account, response);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("success", true, "applicationUrl", destination));
    }

    private String grant(UUID id, String raw, Jwt account, HttpServletResponse response) {
        if (account != null) {
            return applyAccess.grant(id, null, true);
        }
        String identity = guestCookies.identity(raw);
        String destination = applyAccess.grant(id, guestCookies.hash(identity), false);
        if (!identity.equals(raw)) {
            guestCookies.write(response, identity);
        }
        return destination;
    }
}
