package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationReadinessValidator;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
public class CampaignLifecycleService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;
    private final CampaignPublicationValidator publicationValidator;
    private final CampaignApplicationReadinessValidator applicationValidator;

    public enum Action {
        PUBLISH,
        PAUSE,
        RESUME,
        CLOSE
    }

    /**
     * Authorization, parent lock, version check, readiness and transition
     * occur in one transaction.
     */
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse transition(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull Action action
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(() -> error(
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
            throw error(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Campaign changed. Reload it before saving."
            );
        }

        Instant now = Instant.now();

        switch (action) {
            case PUBLISH -> {
                requireStatus(campaign, CampaignStatus.DRAFT);

                var readiness = validateReadiness(campaign, now);

                campaign.publish(now, readiness.formVersionId());
            }
            case PAUSE -> {
                requireStatus(campaign, CampaignStatus.PUBLISHED);
                campaign.pause();
            }
            case RESUME -> {
                requireStatus(campaign, CampaignStatus.PAUSED);

                var readiness = validateReadiness(campaign, now);

                // Never silently change the form accepted at publication.
                if (!Objects.equals(
                        campaign.getPublishedApplicationFormVersionId(),
                        readiness.formVersionId()
                )) {
                    throw error(
                            HttpStatus.CONFLICT,
                            "PUBLISHED_FORM_CHANGED",
                            "The published application form does not match."
                    );
                }

                campaign.resume();
            }
            case CLOSE -> {
                requireStatus(
                        campaign,
                        CampaignStatus.PUBLISHED,
                        CampaignStatus.PAUSED
                );

                campaign.close(now);
            }
        }

        return ManagedCampaignResponse.from(
                campaigns.saveAndFlush(campaign)
        );
    }

    private CampaignApplicationReadinessValidator.Readiness validateReadiness(
            ManagedCampaign campaign,
            Instant now
    ) {
        publicationValidator.validate(campaign, now);

        try {
            return applicationValidator.validateForPublication(campaign);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 409) {
                throw exception;
            }

            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_NOT_READY",
                    "Configure a valid application form or Google Forms destination."
            );
        }
    }

    private void requireStatus(
            ManagedCampaign campaign,
            CampaignStatus... allowed
    ) {
        if (!List.of(allowed).contains(campaign.getStatus())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "This action is not allowed for the current campaign status."
            );
        }
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
}