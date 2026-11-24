package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.campaign.access.ApplyAccessResponse.MissingFieldItem;
import com.adrovis.influencer.campaign.access.CreatorPrefillProfileService.CreatorVault;
import com.adrovis.influencer.entity.CampaignEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.CampaignRepository;
import com.adrovis.influencer.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuestApplyServiceTests {

    private final CampaignService campaigns = mock(CampaignService.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final GuestApplyRepository guests = mock(GuestApplyRepository.class);
    private final CreatorPrefillProfileService profileService = mock(CreatorPrefillProfileService.class);
    private final FormPrefillUrlBuilder prefillBuilder = mock(FormPrefillUrlBuilder.class);
    private final AutoFillAnalyticsService autoFillAnalytics = mock(AutoFillAnalyticsService.class);

    private final CampaignApplyAccessService service = new CampaignApplyAccessService(
            campaigns,
            campaignRepository,
            guests,
            profileService,
            prefillBuilder,
            autoFillAnalytics,
            3,
            GuestApplyCountingMode.CLICKS
    );

    private final UUID campaign = UUID.randomUUID();

    private Jwt mockJwt(String subject) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", subject, "email", "creator@example.com")
        );
    }

    @Test
    void allowsAGuestAndRecordsOnlyAcceptedClicks() {
        when(guests.allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS)).thenReturn(true);
        when(campaigns.trackAndGetApplicationUrl(campaign)).thenReturn("https://forms.example.com/apply");

        assertThat(service.grant(campaign, "hash", false)).isEqualTo("https://forms.example.com/apply");
        verify(guests).allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS);
    }

    @Test
    void blocksTheFourthGuestCampaignBeforeTrackingOrReturningADestination() {
        when(guests.allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS)).thenReturn(false);

        assertThatThrownBy(() -> service.grant(campaign, "hash", false))
                .isInstanceOf(SignupRequiredException.class);
        verify(campaigns, never()).trackAndGetApplicationUrl(any());
    }

    @Test
    void validatedAccountsBypassGuestUsage() {
        when(campaigns.trackAndGetApplicationUrl(campaign)).thenReturn("https://forms.example.com/apply");

        assertThat(service.grant(campaign, null, true)).isEqualTo("https://forms.example.com/apply");
        verifyNoInteractions(guests);
    }

    @Test
    void missingCampaignsDoNotConsumeTheAllowance() {
        when(campaigns.getApplicationDestination(campaign)).thenThrow(new ResourceNotFoundException("Not found"));

        assertThatThrownBy(() -> service.grant(campaign, "hash", false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(guests, never()).allow(anyString(), any(), anyInt(), any());
    }

    @Test
    void returnsMissingFieldsWithoutTrackingClickWhenRequiredProfileDataIsAbsent() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = mockJwt(userId.toString());
        CampaignEntity entity = new CampaignEntity();
        CreatorVault vault = CreatorVault.builder()
                .userId(userId)
                .onboardingComplete(true)
                .scalarValues(Map.of("full_name", "Pranjali Paunikar"))
                .nichesList(List.of("Fashion"))
                .quickCopyData(Map.of("Full Name", "Pranjali Paunikar"))
                .build();

        ApplyAccessResponse missingResp = ApplyAccessResponse.builder()
                .status("MISSING_PROFILE_FIELDS")
                .prefilledCount(11)
                .totalMappedCount(15)
                .missingFields(List.of(MissingFieldItem.builder()
                        .key("followersCount")
                        .label("Instagram Followers")
                        .formQuestionLabel("Instagram Followers Count")
                        .reason("Used to automatically select the right follower tier & payout bracket.")
                        .type("NUMBER")
                        .build()))
                .build();

        when(campaigns.getApplicationDestination(campaign)).thenReturn("https://docs.google.com/forms/d/e/test/viewform");
        when(profileService.loadCreatorVault(jwt)).thenReturn(Optional.of(vault));
        when(campaignRepository.findById(campaign)).thenReturn(Optional.of(entity));
        when(prefillBuilder.evaluateAndBuild(entity, "https://docs.google.com/forms/d/e/test/viewform", vault))
                .thenReturn(missingResp);

        ApplyAccessResponse result = service.grantWithPrefill(campaign, null, jwt);

        assertThat(result.getStatus()).isEqualTo("MISSING_PROFILE_FIELDS");
        assertThat(result.getMissingFields()).hasSize(1);
        verify(campaigns, never()).trackAndGetApplicationUrl(any());
        verifyNoInteractions(autoFillAnalytics);
        verifyNoInteractions(guests);
    }

    @Test
    void returnsPrefilledUrlTracksClickAndRecordsAnalyticsWhenCreatorProfileIsComplete() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = mockJwt(userId.toString());
        CampaignEntity entity = new CampaignEntity();
        CreatorVault vault = CreatorVault.builder()
                .userId(userId)
                .onboardingComplete(true)
                .followersCount(14500L)
                .scalarValues(Map.of("full_name", "Pranjali Paunikar"))
                .nichesList(List.of("Fashion"))
                .quickCopyData(Map.of("Full Name", "Pranjali Paunikar"))
                .build();

        String prefilledUrl = "https://docs.google.com/forms/d/e/test/viewform?usp=pp_url&entry.123=Pranjali+Paunikar";
        ApplyAccessResponse readyResp = ApplyAccessResponse.builder()
                .status("READY")
                .applicationUrl(prefilledUrl)
                .prefilledCount(1)
                .totalMappedCount(1)
                .build();

        when(campaigns.getApplicationDestination(campaign)).thenReturn("https://docs.google.com/forms/d/e/test/viewform");
        when(profileService.loadCreatorVault(jwt)).thenReturn(Optional.of(vault));
        when(campaignRepository.findById(campaign)).thenReturn(Optional.of(entity));
        when(prefillBuilder.evaluateAndBuild(entity, "https://docs.google.com/forms/d/e/test/viewform", vault))
                .thenReturn(readyResp);

        ApplyAccessResponse result = service.grantWithPrefill(campaign, null, jwt);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getApplicationUrl()).isEqualTo(prefilledUrl);
        verify(campaigns).trackAndGetApplicationUrl(campaign);
        verify(autoFillAnalytics).recordApplyWithAutoFill(vault, entity, 1, 1);
        verifyNoInteractions(guests);
    }
}