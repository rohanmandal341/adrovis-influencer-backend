package com.adrovis.influencer.campaignmanagement.application.entity;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "managed_campaign_applications",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_managed_application_campaign_creator",
                columnNames = {"campaign_id", "creator_account_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagedCampaignApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "campaign_title", nullable = false, updatable = false, length = 200)
    private String campaignTitle;

    @Column(name = "creator_account_id", nullable = false, updatable = false)
    private UUID creatorAccountId;

    @Column(name = "creator_email", nullable = false, updatable = false, length = 254)
    private String creatorEmail;

    @Column(name = "form_version_id", nullable = false, updatable = false)
    private UUID formVersionId;

    @Column(name = "form_version_number", nullable = false, updatable = false)
    private int formVersionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode answers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignApplicationStatus status;

    @Column(name = "consent_notice_version", nullable = false, updatable = false, length = 64)
    private String consentNoticeVersion;

    @Column(name = "consent_notice_text", nullable = false, updatable = false, length = 1000)
    private String consentNoticeText;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public ManagedCampaignApplication(
            UUID campaignId,
            String campaignTitle,
            UUID creatorAccountId,
            String creatorEmail,
            UUID formVersionId,
            int formVersionNumber,
            JsonNode answers,
            String consentNoticeVersion,
            String consentNoticeText,
            Instant now
    ) {
        this.campaignId = Objects.requireNonNull(campaignId);
        this.campaignTitle = Objects.requireNonNull(campaignTitle);
        this.creatorAccountId = Objects.requireNonNull(creatorAccountId);
        this.creatorEmail = Objects.requireNonNull(creatorEmail);
        this.formVersionId = Objects.requireNonNull(formVersionId);

        if (formVersionNumber < 1 || answers == null || !answers.isObject()) {
            throw new IllegalArgumentException("Invalid application snapshot.");
        }

        this.formVersionNumber = formVersionNumber;
        this.answers = answers.deepCopy();
        this.consentNoticeVersion = Objects.requireNonNull(consentNoticeVersion);
        this.consentNoticeText = Objects.requireNonNull(consentNoticeText);
        this.consentedAt = Objects.requireNonNull(now);
        this.submittedAt = now;
        this.status = CampaignApplicationStatus.SUBMITTED;
    }

    public JsonNode getAnswers() {
        return answers == null ? null : answers.deepCopy();
    }

    /**
     * Reviewers cannot withdraw applications or return them to SUBMITTED.
     * Accepted/rejected/withdrawn applications are terminal in this version.
     */
    public boolean canReviewAs(CampaignApplicationStatus target) {
        if (target == null) {
            return false;
        }

        return switch (status) {
            case SUBMITTED ->
                    target == CampaignApplicationStatus.UNDER_REVIEW
                            || target == CampaignApplicationStatus.REJECTED;

            case UNDER_REVIEW ->
                    target == CampaignApplicationStatus.SHORTLISTED
                            || target == CampaignApplicationStatus.REJECTED;

            case SHORTLISTED ->
                    target == CampaignApplicationStatus.ACCEPTED
                            || target == CampaignApplicationStatus.REJECTED;

            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }

    public void review(CampaignApplicationStatus target) {
        if (!canReviewAs(target)) {
            throw new IllegalStateException(
                    "Application status transition is not permitted."
            );
        }

        status = target;
    }
}