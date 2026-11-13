package com.adrovis.influencer.campaignmanagement.application.service;

import com.adrovis.influencer.campaignmanagement.application.dto.CreatorCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.SubmitCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import com.adrovis.influencer.campaignmanagement.application.repository.ManagedCampaignApplicationRepository;
import com.adrovis.influencer.campaignmanagement.application.validation.CampaignApplicationAnswerValidator;
import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.campaignmanagement.form.validation.ApplicationConsentNotice;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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
import com.adrovis.influencer.campaignmanagement.application.dto.CreatorApplicationUpdateResponse;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('CREATOR')")
public class CreatorCampaignApplicationService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final ManagedCampaignApplicationRepository applications;
    private final CampaignApplicationAnswerValidator answers;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final CampaignApplicationDecisionRepository decisions;

    @Transactional("primaryTransactionManager")
    public CreatorCampaignApplicationResponse submit(
            @NotNull UUID campaignId,
            @NotNull @Valid SubmitCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();

        var campaign = campaigns.findPublicForApplication(
                campaignId,
                CampaignStatus.PUBLISHED
        ).orElseThrow(() -> error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        ));

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EXTERNAL_APPLICATION_REQUIRED",
                    "This campaign uses an external application form."
            );
        }

        CampaignDetailsRequest details = readValidated(
                campaign.getDetails(),
                CampaignDetailsRequest.class
        );

        Instant now = Instant.now();
        var timeline = details.timeline();

        if (!timeline.closesAt().isAfter(now)
                || ("FIXED".equals(timeline.applicationMode())
                && !timeline.applicationDeadline().isAfter(now))) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_OPEN",
                    "This campaign is not accepting applications."
            );
        }

        if (!request.formVersionId().equals(
                campaign.getPublishedApplicationFormVersionId()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_FORM_CHANGED",
                    "Reload the campaign application form."
            );
        }

        if (applications.existsByCampaignIdAndCreatorAccountId(
                campaignId, account.id()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_ALREADY_EXISTS",
                    "You have already applied to this campaign."
            );
        }

        if (!ApplicationConsentNotice.VERSION.equals(
                request.consent().noticeVersion()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CONSENT_NOTICE_CHANGED",
                    "Reload and review the current application notice."
            );
        }

        var form = forms.findByIdAndCampaign_Id(
                request.formVersionId(),
                campaignId
        ).orElseThrow(this::dataUnavailable);

        CreateApplicationFormVersionRequest definition = readValidated(
                form.getDefinition(),
                CreateApplicationFormVersionRequest.class
        );

        JsonNode normalizedAnswers = answers.validate(
                definition.fields(),
                request.answers()
        );

        ManagedCampaignApplication application =
                new ManagedCampaignApplication(
                        campaignId,
                        campaign.getTitle(),
                        account.id(),
                        account.email(),
                        form.getId(),
                        form.getVersionNumber(),
                        normalizedAnswers,
                        ApplicationConsentNotice.VERSION,
                        ApplicationConsentNotice.TEXT,
                        now
                );

        return CreatorCampaignApplicationResponse.from(
                applications.saveAndFlush(application)
        );
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<CreatorCampaignApplicationResponse> listMine(
            int page,
            int size
    ) {
        var account = currentAccount.require();

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

        return applications
                .findAllByCreatorAccountId(account.id(), pagination)
                .map(CreatorCampaignApplicationResponse::from);
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public CreatorCampaignApplicationResponse getMine(
            @NotNull UUID applicationId
    ) {
        var account = currentAccount.require();

        return applications
                .findByIdAndCreatorAccountId(applicationId, account.id())
                .map(CreatorCampaignApplicationResponse::from)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND",
                        "Application not found."
                ));
    }

    private <T> T readValidated(JsonNode snapshot, Class<T> type) {
        if (snapshot == null) {
            throw dataUnavailable();
        }

        try {
            T value = objectMapper.treeToValue(snapshot, type);

            if (value == null || !validator.validate(value).isEmpty()) {
                throw dataUnavailable();
            }

            return value;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw dataUnavailable();
        }
    }

    private CampaignManagementException dataUnavailable() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "APPLICATION_DATA_UNAVAILABLE",
                "Application configuration is temporarily unavailable."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public List<CreatorApplicationUpdateResponse> updates(
            @NotNull UUID applicationId
    ) {
        var account = currentAccount.require();

        applications.findByIdAndCreatorAccountId(applicationId, account.id())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND",
                        "Application not found."
                ));

        return decisions
                .findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId)
                .stream()
                .map(CreatorApplicationUpdateResponse::from)
                .toList();
    }
}