package com.adrovis.influencer.campaignmanagement.application.service;

import com.adrovis.influencer.campaignmanagement.application.dto.ApplicationDecisionResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherApplicationDetailResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.ReviewCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import com.adrovis.influencer.campaignmanagement.application.repository.ManagedCampaignApplicationRepository;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
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

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
public class PublisherCampaignApplicationService {

    private final ManagedCampaignRepository campaigns;
    private final ManagedCampaignApplicationRepository applications;
    private final CampaignApplicationDecisionRepository decisions;
    private final CampaignApplicationFormVersionRepository forms;
    private final CurrentAccount currentAccount;

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<PublisherCampaignApplicationResponse> list(
            @NotNull UUID campaignId,
            CampaignApplicationStatus status,
            int page,
            int size
    ) {
        authorizeRead(campaignId);

        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        PageRequest pagination = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("submittedAt"),
                        Sort.Order.desc("id")
                )
        );

        var results = status == null
                ? applications.findAllByCampaignId(campaignId, pagination)
                : applications.findAllByCampaignIdAndStatus(
                campaignId, status, pagination
        );

        return results.map(PublisherCampaignApplicationResponse::from);
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public PublisherApplicationDetailResponse detail(
            @NotNull UUID campaignId,
            @NotNull UUID applicationId
    ) {
        authorizeRead(campaignId);

        var application = applications
                .findByIdAndCampaignId(applicationId, campaignId)
                .orElseThrow(this::applicationNotFound);

        var form = forms.findByIdAndCampaign_Id(
                application.getFormVersionId(),
                campaignId
        ).orElseThrow(() -> error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "APPLICATION_DATA_UNAVAILABLE",
                "Application configuration is temporarily unavailable."
        ));

        var history = decisions
                .findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId)
                .stream()
                .map(ApplicationDecisionResponse::from)
                .toList();

        return new PublisherApplicationDetailResponse(
                PublisherCampaignApplicationResponse.from(application),
                ApplicationFormVersionResponse.from(form),
                history
        );
    }

    /**
     * Always locks campaign first, application second.
     * Submission/lifecycle/review share the parent campaign lock.
     */
    @Transactional("primaryTransactionManager")
    public PublisherCampaignApplicationResponse review(
            @NotNull UUID campaignId,
            @NotNull UUID applicationId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid ReviewCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(this::campaignNotFound);

        authorizePublisherRole(campaign, account);

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw error(
                    HttpStatus.CONFLICT,
                    "PUBLISHER_ARCHIVED",
                    "The campaign publisher is archived."
            );
        }

        // Closed campaigns may still need their existing applicants reviewed.
        if (campaign.getStatus() != CampaignStatus.PUBLISHED
                && campaign.getStatus() != CampaignStatus.PAUSED
                && campaign.getStatus() != CampaignStatus.CLOSED) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_REVIEWABLE",
                    "Applications cannot be reviewed in this campaign state."
            );
        }

        var application = applications.findForReview(
                applicationId,
                campaignId
        ).orElseThrow(this::applicationNotFound);

        if (application.getVersion() == null
                || application.getVersion().longValue() != expectedVersion) {
            throw error(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Application changed. Reload it before reviewing."
            );
        }

        if (!application.canReviewAs(request.status())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "This application status transition is not allowed."
            );
        }

        var previousStatus = application.getStatus();

        application.review(request.status());

        decisions.save(new CampaignApplicationDecision(
                application.getId(),
                account.id(),
                previousStatus,
                application.getStatus(),
                request.internalNote(),
                request.creatorMessage(),
                Instant.now()
        ));

        // Both the status update and decision record commit or roll back together.
        return PublisherCampaignApplicationResponse.from(
                applications.saveAndFlush(application)
        );
    }

    /**
     * Authorized owners/admins retain historical read access after archiving.
     * No other account can obtain applicant data by knowing the campaign ID.
     */
    private void authorizeRead(UUID campaignId) {
        var account = currentAccount.require();

        ManagedCampaign campaign = campaigns.findById(campaignId)
                .orElseThrow(this::campaignNotFound);

        boolean admin = account.roles().contains("ADMIN");

        if (!admin && !campaign.getPublisherProfile()
                .getOwnerAccountId().equals(account.id())) {
            throw campaignNotFound();
        }

        authorizePublisherRole(campaign, account);
    }

    private void authorizePublisherRole(
            ManagedCampaign campaign,
            CurrentAccount.Identity account
    ) {
        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!account.roles().contains("ADMIN")
                && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }
    }

    private CampaignManagementException campaignNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        );
    }

    private CampaignManagementException applicationNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "APPLICATION_NOT_FOUND",
                "Application not found."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
}