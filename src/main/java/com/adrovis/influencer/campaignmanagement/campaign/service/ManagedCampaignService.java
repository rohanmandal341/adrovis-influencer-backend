package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CreateManagedCampaignRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.repository.PublisherProfileRepository;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class ManagedCampaignService {

    private final ManagedCampaignRepository campaigns;
    private final PublisherProfileRepository publishers;
    private final CurrentAccount currentAccount;

    /**
     * Brand/agency accounts create under their own active publisher profile.
     * Admins may create on behalf of any active publisher.
     * Every campaign starts as DRAFT.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse create(
            @NotNull @Valid CreateManagedCampaignRequest request
    ) {
        var account = currentAccount.require();

        PublisherProfile publisher = resolvePublisherForCreation(
                request.publisherProfileId(),
                account
        );

        ManagedCampaign campaign = new ManagedCampaign(
                publisher,
                account.id(),
                request.title(),
                request.description(),
                request.collaborationType()
        );

        ManagedCampaign saved = campaigns.saveAndFlush(campaign);

        return ManagedCampaignResponse.from(saved);
    }

    /**
     * Admins see all non-archived campaigns.
     * Other publishers see only campaigns owned by their authenticated account.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<ManagedCampaignResponse> list(int page, int size) {
        var account = currentAccount.require();

        PageRequest pagination = pagination(page, size);

        Page<ManagedCampaign> results;

        if (isAdmin(account)) {
            results = campaigns.findAllByArchivedAtIsNull(pagination);
        } else {
            results = campaigns
                    .findAllByPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
                            account.id(),
                            pagination
                    );
        }

        // Map inside the transaction; entities never leave the service.
        return results.map(ManagedCampaignResponse::from);
    }

    /**
     * Returns management details only to the owner or an admin.
     * Unrelated and missing campaigns both return 404.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public ManagedCampaignResponse getById(@NotNull UUID campaignId) {
        var account = currentAccount.require();

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

        return ManagedCampaignResponse.from(campaign);
    }

    /**
     * Publisher ID is a selection, not proof of ownership.
     * Non-admin lookups include the authenticated owner in the database query.
     */
    private PublisherProfile resolvePublisherForCreation(
            UUID publisherProfileId,
            CurrentAccount.Identity account
    ) {
        if (isAdmin(account)) {
            return publishers
                    .findByIdAndArchivedAtIsNull(publisherProfileId)
                    .orElseThrow(this::publisherNotFound);
        }

        PublisherProfile publisher = publishers
                .findByIdAndOwnerAccountIdAndArchivedAtIsNull(
                        publisherProfileId,
                        account.id()
                )
                .orElseThrow(this::publisherNotFound);

        // Ownership alone does not restore a role removed from the account.
        if (!account.roles().contains(publisher.getPublisherType().name())) {
            throw new AccessDeniedException(
                    "Your account cannot create campaigns for this publisher type."
            );
        }

        return publisher;
    }

    /**
     * Uses bounded pagination and fixed sorting instead of accepting
     * arbitrary database property names from request input.
     */
    private PageRequest pagination(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        return PageRequest.of(page, size, sort);
    }

    private boolean isAdmin(CurrentAccount.Identity account) {
        return account.roles().contains("ADMIN");
    }

    private ResponseStatusException publisherNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Publisher profile not found."
        );
    }

    private ResponseStatusException campaignNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        );
    }
}