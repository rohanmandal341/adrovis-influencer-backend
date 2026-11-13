package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CampaignApplicationReadinessValidator {

    private final CampaignApplicationFormVersionRepository forms;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Application-specific publishing check.
     *
     * The future publication service must authorize the caller, lock the
     * campaign and call this inside the same primary database transaction.
     */
    public Readiness validateForPublication(ManagedCampaign campaign) {
        if (campaign.getApplicationMode() == ApplicationMode.GOOGLE_FORM) {
            if (!GoogleFormUrl.isValid(campaign.getExternalApplicationUrl())) {
                throw conflict(
                        "A valid Google Forms destination is required."
                );
            }

            return new Readiness(ApplicationMode.GOOGLE_FORM, null);
        }

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw conflict("Application mode is not configured.");
        }

        if (campaign.getExternalApplicationUrl() != null) {
            throw conflict(
                    "Platform applications must not have an external URL."
            );
        }

        var version = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(
                        campaign.getId()
                )
                .orElseThrow(() -> conflict(
                        "Create an application form before publishing."
                ));

        CreateApplicationFormVersionRequest definition;

        try {
            definition = objectMapper.treeToValue(
                    version.getDefinition(),
                    CreateApplicationFormVersionRequest.class
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw conflict("The stored application form is invalid.");
        }

        if (definition == null
                || !validator.validate(definition).isEmpty()) {
            throw conflict("The stored application form is invalid.");
        }

        // Publication must later pin this exact immutable form-version ID.
        return new Readiness(ApplicationMode.PLATFORM, version.getId());
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record Readiness(
            ApplicationMode mode,
            UUID formVersionId
    ) {
    }
}