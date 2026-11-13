package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.dto.UpdateCampaignDraftRequest;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
@Transactional("primaryTransactionManager")
public class ManagedCampaignEditService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;

    public ManagedCampaignResponse updateDraft(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid UpdateCampaignDraftRequest request
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        requireEditableDraft(campaign);

        campaign.updateDraft(
                request.title(),
                request.description(),
                request.collaborationType()
        );

        return save(campaign);
    }

    /**
     * Replaces the entire details snapshot.
     * Does not change application mode or immutable form versions.
     */
    public ManagedCampaignResponse updateDetails(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid CampaignDetailsRequest request
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        requireEditableDraft(campaign);

        campaign.configureDetails(objectMapper.valueToTree(request));

        return save(campaign);
    }

    /**
     * Archives instead of deleting campaign/application records.
     * An archived publisher does not prevent its owner/admin archiving a campaign.
     */
    public ManagedCampaignResponse archive(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        campaign.archive(Instant.now());

        return save(campaign);
    }

    private ManagedCampaign lockAuthorized(
            UUID campaignId,
            long expectedVersion
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(() -> new CampaignManagementException(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
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
            throw new CampaignManagementException(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Campaign changed. Reload it before saving."
            );
        }

        return campaign;
    }

    private void requireEditableDraft(ManagedCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignManagementException(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_EDITABLE",
                    "Only draft campaigns can be edited."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new CampaignManagementException(
                    HttpStatus.CONFLICT,
                    "PUBLISHER_ARCHIVED",
                    "The campaign publisher is archived."
            );
        }
    }

    private ManagedCampaignResponse save(ManagedCampaign campaign) {
        return ManagedCampaignResponse.from(
                campaigns.saveAndFlush(campaign)
        );
    }
}