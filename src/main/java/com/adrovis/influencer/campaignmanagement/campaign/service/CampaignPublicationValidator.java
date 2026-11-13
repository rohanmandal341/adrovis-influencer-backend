package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CampaignPublicationValidator {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CampaignPublicationValidator(
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Validates campaign business details before publication or resumption.
     * Authentication, ownership, locking and lifecycle checks remain
     * the responsibility of CampaignLifecycleService.
     */
    public void validate(ManagedCampaign campaign, Instant now) {
        if (campaign == null || now == null) {
            throw new IllegalArgumentException(
                    "Campaign and validation time are required."
            );
        }

        if (campaign.getPublisherProfile() == null
                || campaign.getPublisherProfile().getArchivedAt() != null) {
            throw notReady("An active publisher profile is required.");
        }

        if (campaign.getTitle() == null
                || campaign.getTitle().isBlank()
                || campaign.getTitle().length() > 200) {
            throw notReady(
                    "A campaign title containing 1 to 200 characters is required."
            );
        }

        if (campaign.getDescription() == null
                || campaign.getDescription().isBlank()
                || campaign.getDescription().length() > 10000) {
            throw notReady(
                    "A campaign description containing 1 to 10000 characters is required."
            );
        }

        if (campaign.getCollaborationType() == null) {
            throw notReady("Collaboration type is required.");
        }

        CampaignDetailsRequest details = readAndValidateDetails(
                campaign.getDetails()
        );

        validateCompensation(campaign, details);
        validateTimeline(details.timeline(), now);
        validateDeliverablePlatforms(details);
    }

    /**
     * Converts the stored JSON snapshot and applies all nested
     * Jakarta Bean Validation rules from CampaignDetailsRequest.
     */
    private CampaignDetailsRequest readAndValidateDetails(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw notReady(
                    "Complete the campaign details before publishing."
            );
        }

        CampaignDetailsRequest details;

        try {
            details = objectMapper.treeToValue(
                    json,
                    CampaignDetailsRequest.class
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw notReady("Stored campaign details are invalid.");
        }

        if (details == null || !validator.validate(details).isEmpty()) {
            throw notReady(
                    "Campaign details contain missing or invalid fields."
            );
        }

        return details;
    }

    private void validateCompensation(
            ManagedCampaign campaign,
            CampaignDetailsRequest details
    ) {
        CampaignDetailsRequest.Compensation compensation =
                details.compensation();

        boolean productProvided = Boolean.TRUE.equals(
                compensation.productProvided()
        );

        boolean productHasValue = positive(
                compensation.productValue()
        );

        boolean cashHasValue = positive(
                compensation.cashAmount()
        );

        if (productProvided
                && (details.product() == null || !productHasValue)) {
            throw notReady(
                    "A gifted product requires product details and a positive value."
            );
        }

        if (!productProvided && productHasValue) {
            throw notReady(
                    "Product value cannot be set when no product is provided."
            );
        }

        switch (campaign.getCollaborationType()) {
            case BARTER -> {
                if (!productProvided || !productHasValue || cashHasValue) {
                    throw notReady(
                            "Barter campaigns require a gifted product and no cash payment."
                    );
                }
            }

            case PAID -> {
                if (!cashHasValue) {
                    throw notReady(
                            "Paid campaigns require a positive cash amount."
                    );
                }
            }

            case HYBRID -> {
                if (!cashHasValue || !productProvided || !productHasValue) {
                    throw notReady(
                            "Hybrid campaigns require cash and a gifted product."
                    );
                }
            }
        }
    }

    private void validateTimeline(
            CampaignDetailsRequest.Timeline timeline,
            Instant now
    ) {
        // Nested Bean Validation has already checked required fields.
        if (!timeline.closesAt().isAfter(now)) {
            throw notReady(
                    "The campaign closing date must be in the future."
            );
        }

        Instant deadline = timeline.applicationDeadline();
        Instant submission = timeline.contentSubmissionAt();
        Instant live = timeline.contentLiveAt();

        if ("FIXED".equals(timeline.applicationMode())
                && (deadline == null || !deadline.isAfter(now))) {
            throw notReady(
                    "A fixed application deadline must be in the future."
            );
        }

        if (submission != null && !submission.isAfter(now)) {
            throw notReady(
                    "Content submission must be in the future."
            );
        }

        if (live != null && !live.isAfter(now)) {
            throw notReady(
                    "Content publication must be in the future."
            );
        }

        if (deadline != null) {
            if (submission != null && deadline.isAfter(submission)) {
                throw notReady(
                        "Application deadline cannot follow content submission."
                );
            }

            if (live != null && deadline.isAfter(live)) {
                throw notReady(
                        "Application deadline cannot follow content publication."
                );
            }
        }
    }

    private void validateDeliverablePlatforms(
            CampaignDetailsRequest details
    ) {
        for (CampaignDetailsRequest.Deliverable deliverable
                : details.deliverables()) {

            if (!details.eligibility().platforms()
                    .contains(deliverable.platform())) {
                throw notReady(
                        "Deliverable platforms must match campaign eligibility."
                );
            }
        }
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    private CampaignManagementException notReady(String message) {
        return new CampaignManagementException(
                HttpStatus.CONFLICT,
                "CAMPAIGN_NOT_READY",
                message
        );
    }
}