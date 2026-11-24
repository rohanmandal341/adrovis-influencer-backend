package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CampaignPrefillController {

    private final CreatorPrefillProfileService profileService;
    private final AutoFillAnalyticsService analyticsService;

    @GetMapping("/api/v1/campaigns/prefill-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CreatorPrefillProfileResponse>> getMyPrefillProfile(
            @AuthenticationPrincipal Jwt account) {
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfileResponse(account)));
    }

    @PatchMapping("/api/v1/campaigns/prefill-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updatePrefillProfile(
            @RequestBody UpdateCreatorPrefillProfileRequest request,
            @AuthenticationPrincipal Jwt account) {
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<String> updatedKeys = profileService.updateProfile(account, request);
        profileService.loadCreatorVault(account)
                .ifPresent(vault -> analyticsService.recordProgressiveFieldsSaved(vault, updatedKeys));
        return ResponseEntity.ok(ApiResponse.ok("Auto-fill profile updated", null));
    }

    @GetMapping("/api/v1/admin/autofill-analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAutoFillAnalytics() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAdminAnalyticsSummary()));
    }
}