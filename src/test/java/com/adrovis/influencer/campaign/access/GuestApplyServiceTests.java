package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.service.CampaignService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    private final GuestApplyRepository guests = mock(GuestApplyRepository.class);
    private final CampaignApplyAccessService service = new CampaignApplyAccessService(campaigns, guests, 3);
    private final UUID campaign = UUID.randomUUID();

    @Test
    void allowsAGuestAndRecordsOnlyAcceptedClicks() {
        when(guests.allow("hash", campaign, 3)).thenReturn(true);
        when(campaigns.trackAndGetApplicationUrl(campaign)).thenReturn("https://forms.example.com/apply");
        assertThat(service.grant(campaign, "hash", false)).isEqualTo("https://forms.example.com/apply");
        verify(guests).allow("hash", campaign, 3);
    }

    @Test
    void blocksTheFourthGuestCampaignBeforeTrackingOrReturningADestination() {
        when(guests.allow("hash", campaign, 3)).thenReturn(false);
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
        verify(guests, never()).allow(anyString(), any(), anyInt());
    }
}
