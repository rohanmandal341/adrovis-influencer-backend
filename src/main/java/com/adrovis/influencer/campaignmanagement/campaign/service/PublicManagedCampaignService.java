package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.PublicManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(
        value = "primaryTransactionManager",
        readOnly = true
)
public class PublicManagedCampaignService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Returns public published campaigns.
     * A published campaign whose deadline has passed remains viewable,
     * but acceptingApplications is false.
     */
    public Page<PublicManagedCampaignResponse> list(int page, int size) {
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
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.desc("id")
                )
        );

        Instant now = Instant.now();

        return campaigns
                .findPublicCampaigns(CampaignStatus.PUBLISHED, pagination)
                .map(campaign -> toPublicResponse(campaign, now));
    }

    public PublicManagedCampaignResponse getById(
            @NotNull UUID campaignId
    ) {
        return toPublicResponse(
                findPublic(campaignId),
                Instant.now()
        );
    }

    /**
     * Creator receives the exact immutable form pinned at publication.
     * Owner/admin management endpoints still use their separate form API.
     */
    @PreAuthorize("hasRole('CREATOR')")
    public ApplicationFormVersionResponse getPublishedForm(
            @NotNull UUID campaignId
    ) {
        ManagedCampaign campaign = findPublic(campaignId);
        CampaignDetailsRequest details = readDetails(campaign);

        if (!acceptingApplications(details, Instant.now())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_OPEN",
                    "This campaign is not accepting applications."
            );
        }

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EXTERNAL_APPLICATION_REQUIRED",
                    "This campaign uses an external application form."
            );
        }

        UUID formId = campaign.getPublishedApplicationFormVersionId();

        if (formId == null) {
            throw applicationUnavailable();
        }

        var form = forms
                .findByIdAndCampaign_Id(formId, campaign.getId())
                .orElseThrow(this::applicationUnavailable);

        return ApplicationFormVersionResponse.from(form);
    }

    private ManagedCampaign findPublic(UUID campaignId) {
        return campaigns
                .findPublicCampaign(campaignId, CampaignStatus.PUBLISHED)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "CAMPAIGN_NOT_FOUND",
                        "Campaign not found."
                ));
    }

    private PublicManagedCampaignResponse toPublicResponse(
            ManagedCampaign campaign,
            Instant now
    ) {
        CampaignDetailsRequest details = readDetails(campaign);
        var publisher = campaign.getPublisherProfile();

        return new PublicManagedCampaignResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                new PublicManagedCampaignResponse.PublisherSummary(
                        publisher.getId(),
                        publisher.getPublisherType(),
                        publisher.getDisplayName()
                ),
                campaign.getCollaborationType(),
                details,
                campaign.getApplicationMode(),
                acceptingApplications(details, now),
                campaign.getPublishedAt()
        );
    }

    private CampaignDetailsRequest readDetails(ManagedCampaign campaign) {
        if (campaign.getDetails() == null) {
            throw applicationUnavailable();
        }

        try {
            CampaignDetailsRequest details = objectMapper.treeToValue(
                    campaign.getDetails(),
                    CampaignDetailsRequest.class
            );

            if (details == null || !validator.validate(details).isEmpty()) {
                throw applicationUnavailable();
            }

            return details;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw applicationUnavailable();
        }
    }

    private boolean acceptingApplications(
            CampaignDetailsRequest details,
            Instant now
    ) {
        var timeline = details.timeline();

        if (!timeline.closesAt().isAfter(now)) {
            return false;
        }

        return !"FIXED".equals(timeline.applicationMode())
                || timeline.applicationDeadline().isAfter(now);
    }

    private CampaignManagementException applicationUnavailable() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CAMPAIGN_DATA_UNAVAILABLE",
                "Campaign application data is temporarily unavailable."
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