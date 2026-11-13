package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.form.dto.ConfigureCampaignApplicationRequest;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
public class CampaignApplicationSettingsService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;

    /**
     * Changes application settings on an authorized draft.
     * Uses the same campaign lock as form-version creation.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse configure(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid ConfigureCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId,
                        account.id()
                )
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        ));

        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!admin && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }

        if (campaign.getVersion() == null
                || campaign.getVersion().longValue() != expectedVersion) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "Campaign changed. Reload it before saving."
            );
        }

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application settings can only change on draft campaigns."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The campaign publisher is archived."
            );
        }

        campaign.configureApplication(
                request.mode(),
                request.externalUrl()
        );

        // Flush so the returned DTO contains the current optimistic version.
        ManagedCampaign saved = campaigns.saveAndFlush(campaign);

        return ManagedCampaignResponse.from(saved);
    }
}