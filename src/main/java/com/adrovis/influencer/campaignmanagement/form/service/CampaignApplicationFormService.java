package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
public class CampaignApplicationFormService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new immutable form version.
     * Locking the parent campaign serializes version allocation.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ApplicationFormVersionResponse createVersion(
            @NotNull UUID campaignId,
            @NotNull @Valid CreateApplicationFormVersionRequest request
    ) {
        var account = currentAccount.require();

        ManagedCampaign campaign = lockAuthorizedCampaign(
                campaignId,
                account
        );

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application forms can only be configured on draft campaigns."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An archived publisher cannot configure campaign forms."
            );
        }

        int previousVersion = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(campaignId)
                .map(CampaignApplicationFormVersion::getVersionNumber)
                .orElse(0);

        if (previousVersion == Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No further form versions can be created."
            );
        }

        // Persist a detached JSON snapshot, not the mutable request object.
        JsonNode definition = objectMapper.valueToTree(request);

        CampaignApplicationFormVersion version =
                new CampaignApplicationFormVersion(
                        campaign,
                        previousVersion + 1,
                        definition,
                        account.id()
                );

        CampaignApplicationFormVersion saved = forms.save(version);

        return ApplicationFormVersionResponse.from(saved);
    }

    /**
     * Returns the latest form configuration to the owner or admin.
     * This is a management operation, not the public creator form endpoint.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public ApplicationFormVersionResponse getLatest(
            @NotNull UUID campaignId
    ) {
        var account = currentAccount.require();

        ManagedCampaign campaign = findAuthorizedCampaign(
                campaignId,
                account
        );

        CampaignApplicationFormVersion version = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(
                        campaign.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application form not found."
                ));

        return ApplicationFormVersionResponse.from(version);
    }

    /**
     * Uses ownership-scoped locking for non-admin accounts.
     * Missing and unrelated campaigns both return 404.
     */
    private ManagedCampaign lockAuthorizedCampaign(
            UUID campaignId,
            CurrentAccount.Identity account
    ) {
        ManagedCampaign campaign;

        if (isAdmin(account)) {
            campaign = campaigns.findForFormUpdate(campaignId)
                    .orElseThrow(this::campaignNotFound);
        } else {
            campaign = campaigns.findOwnedForFormUpdate(
                            campaignId,
                            account.id()
                    )
                    .orElseThrow(this::campaignNotFound);
        }

        verifyPublisherRole(campaign, account);

        return campaign;
    }

    private ManagedCampaign findAuthorizedCampaign(
            UUID campaignId,
            CurrentAccount.Identity account
    ) {
        ManagedCampaign campaign;

        if (isAdmin(account)) {
            campaign = campaigns.findByIdAndArchivedAtIsNull(campaignId)
                    .orElseThrow(this::campaignNotFound);
        } else {
            campaign = campaigns
                    .findByIdAndPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
                            campaignId,
                            account.id()
                    )
                    .orElseThrow(this::campaignNotFound);
        }

        verifyPublisherRole(campaign, account);

        return campaign;
    }

    /**
     * An account must still hold its publisher role.
     * Admin authority explicitly permits cross-publisher management.
     */
    private void verifyPublisherRole(
            ManagedCampaign campaign,
            CurrentAccount.Identity account
    ) {
        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!isAdmin(account) && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher's forms."
            );
        }
    }

    private boolean isAdmin(CurrentAccount.Identity account) {
        return account.roles().contains("ADMIN");
    }

    private ResponseStatusException campaignNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        );
    }
}