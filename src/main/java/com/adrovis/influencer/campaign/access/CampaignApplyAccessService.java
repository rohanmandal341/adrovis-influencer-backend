package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.campaign.access.CreatorPrefillProfileService.CreatorVault;
import com.adrovis.influencer.entity.CampaignEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.CampaignRepository;
import com.adrovis.influencer.service.CampaignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CampaignApplyAccessService {

    private final CampaignService campaigns;
    private final CampaignRepository campaignRepository;
    private final GuestApplyRepository guests;
    private final CreatorPrefillProfileService profileService;
    private final FormPrefillUrlBuilder prefillBuilder;
    private final AutoFillAnalyticsService autoFillAnalytics;
    private final int limit;
    private final GuestApplyCountingMode mode;

    public CampaignApplyAccessService(
            CampaignService campaigns,
            GuestApplyRepository guests,
            int limit,
            GuestApplyCountingMode mode) {
        this(campaigns, null, guests, null, null, null, limit, mode);
    }

    @Autowired
    public CampaignApplyAccessService(
            CampaignService campaigns,
            CampaignRepository campaignRepository,
            GuestApplyRepository guests,
            CreatorPrefillProfileService profileService,
            FormPrefillUrlBuilder prefillBuilder,
            AutoFillAnalyticsService autoFillAnalytics,
            @Value("${app.guest-apply.limit:3}") int limit,
            @Value("${app.guest-apply.counting-mode:CLICKS}") GuestApplyCountingMode mode) {
        if (limit < 1 || limit > 100) {
            throw new IllegalStateException("Guest campaign allowance must be between 1 and 100");
        }
        this.campaigns = campaigns;
        this.campaignRepository = campaignRepository;
        this.guests = guests;
        this.profileService = profileService;
        this.prefillBuilder = prefillBuilder;
        this.autoFillAnalytics = autoFillAnalytics;
        this.limit = limit;
        this.mode = Objects.requireNonNull(mode, "Configure a guest Apply counting mode");
    }

    @Transactional("primaryTransactionManager")
    public String grant(UUID campaignId, String guestHash, boolean authenticated) {
        campaigns.getApplicationDestination(campaignId);
        if (!authenticated && !guests.allow(guestHash, campaignId, limit, mode)) {
            throw new SignupRequiredException();
        }
        return campaigns.trackAndGetApplicationUrl(campaignId);
    }

    @Transactional("primaryTransactionManager")
    public ApplyAccessResponse grantWithPrefill(UUID campaignId, String guestHash, Jwt account) {
        String rawDestination = campaigns.getApplicationDestination(campaignId);

        if (account == null) {
            if (!guests.allow(guestHash, campaignId, limit, mode)) {
                throw new SignupRequiredException();
            }
            String trackedUrl = campaigns.trackAndGetApplicationUrl(campaignId);
            return ApplyAccessResponse.builder()
                    .status("READY")
                    .applicationUrl(trackedUrl)
                    .prefilledCount(0)
                    .build();
        }

        if (profileService == null || prefillBuilder == null || campaignRepository == null) {
            String trackedUrl = campaigns.trackAndGetApplicationUrl(campaignId);
            return ApplyAccessResponse.builder()
                    .status("READY")
                    .applicationUrl(trackedUrl)
                    .prefilledCount(0)
                    .build();
        }

        Optional<CreatorVault> vaultOpt = profileService.loadCreatorVault(account);
        if (vaultOpt.isEmpty()) {
            String trackedUrl = campaigns.trackAndGetApplicationUrl(campaignId);
            return ApplyAccessResponse.builder()
                    .status("READY")
                    .applicationUrl(trackedUrl)
                    .prefilledCount(0)
                    .build();
        }

        CreatorVault vault = vaultOpt.get();
        if (!vault.isOnboardingComplete()) {
            return ApplyAccessResponse.builder()
                    .status("ONBOARDING_REQUIRED")
                    .build();
        }

        CampaignEntity entity = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + campaignId));

        ApplyAccessResponse result = prefillBuilder.evaluateAndBuild(entity, rawDestination, vault);
        if ("READY".equals(result.getStatus())) {
            campaigns.trackAndGetApplicationUrl(campaignId);
            if (autoFillAnalytics != null) {
                autoFillAnalytics.recordApplyWithAutoFill(
                        vault,
                        entity,
                        result.getPrefilledCount() != null ? result.getPrefilledCount() : 0,
                        result.getTotalMappedCount() != null ? result.getTotalMappedCount() : 0
                );
            }
        }
        return result;
    }
}