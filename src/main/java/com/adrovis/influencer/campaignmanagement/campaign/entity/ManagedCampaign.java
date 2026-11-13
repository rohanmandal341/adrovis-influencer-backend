package com.adrovis.influencer.campaignmanagement.campaign.entity;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "managed_campaigns", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagedCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The publisher owns the campaign.
    // No cascading deletion: deleting a campaign must not delete its publisher.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "publisher_profile_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_managed_campaign_publisher")
    )
    private PublisherProfile publisherProfile;

    // Records the actual creator, including an admin acting for a publisher.
    // Derived from authenticated identity, never trusted from request JSON.
    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_type", nullable = false, length = 20)
    private CollaborationType collaborationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Creates a draft. Publishing will require a separate validated action.
     * The service must authorize access to the selected publisher first.
     */
    public ManagedCampaign(
            PublisherProfile publisherProfile,
            UUID createdByAccountId,
            String title,
            String description,
            CollaborationType collaborationType
    ) {
        this.publisherProfile = Objects.requireNonNull(
                publisherProfile, "Publisher profile is required."
        );

        Objects.requireNonNull(
                publisherProfile.getId(), "Publisher must already exist."
        );

        if (publisherProfile.getArchivedAt() != null) {
            throw new IllegalArgumentException(
                    "An archived publisher cannot create campaigns."
            );
        }

        this.createdByAccountId = Objects.requireNonNull(
                createdByAccountId, "Creating account is required."
        );

        this.collaborationType = Objects.requireNonNull(
                collaborationType, "Collaboration type is required."
        );

        if (title == null
                || title.isBlank()
                || title.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Title must contain 1 to 200 characters."
            );
        }

        String cleanDescription = description == null
                ? null
                : description.trim();

        if (cleanDescription != null && cleanDescription.length() > 10000) {
            throw new IllegalArgumentException(
                    "Description must not exceed 10000 characters."
            );
        }

        this.title = title.trim();
        this.description = cleanDescription;
        this.status = CampaignStatus.DRAFT;
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    // New campaigns use Brandcollabz applications unless configured otherwise.
    @Enumerated(EnumType.STRING)
    @Column(name = "application_mode", nullable = false, length = 20)
    private ApplicationMode applicationMode = ApplicationMode.PLATFORM;

    // Private management field. Do not expose directly in public campaign details.
    @Column(name = "external_application_url", length = 2048)
    private String externalApplicationUrl;

    /**
     * Changes application settings only while the campaign is a draft.
     * The service must authorize the caller and check the edit version first.
     */
    public void configureApplication(
            ApplicationMode mode,
            String externalUrl
    ) {
        Objects.requireNonNull(mode, "Application mode is required.");

        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Application settings can only change on draft campaigns."
            );
        }

        String normalizedUrl = externalUrl == null || externalUrl.isBlank()
                ? null
                : externalUrl.trim();

        if (mode == ApplicationMode.PLATFORM && normalizedUrl != null) {
            throw new IllegalArgumentException(
                    "Platform applications must not include an external URL."
            );
        }

        if (mode == ApplicationMode.GOOGLE_FORM
                && !GoogleFormUrl.isValid(normalizedUrl)) {
            throw new IllegalArgumentException(
                    "A supported HTTPS Google Forms URL is required."
            );
        }

        applicationMode = mode;
        externalApplicationUrl = normalizedUrl;
    }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private JsonNode details;

    /**
     * Replaces the complete details snapshot on a draft.
     * The service validates the DTO, ownership and expected version first.
     */
    public void configureDetails(JsonNode definition) {
        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Campaign details can only change on draft campaigns."
            );
        }

        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException(
                    "Campaign details must be a JSON object."
            );
        }

        details = definition.deepCopy();
    }

    public JsonNode getDetails() {
        return details == null ? null : details.deepCopy();
    }
    /**
     * Replaces only the editable basic fields of a draft.
     * Ownership and version checks belong to the service.
     */
    public void updateDraft(
            String title,
            String description,
            CollaborationType collaborationType
    ) {
        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Only draft campaigns can be edited."
            );
        }

        String cleanTitle = title == null ? null : title.trim();
        String cleanDescription = description == null
                ? null
                : description.trim();

        if (cleanTitle == null
                || cleanTitle.isBlank()
                || cleanTitle.length() > 200) {
            throw new IllegalArgumentException("Invalid campaign title.");
        }

        if (cleanDescription != null && cleanDescription.length() > 10000) {
            throw new IllegalArgumentException("Campaign description is too long.");
        }

        this.title = cleanTitle;
        this.description = cleanDescription;
        this.collaborationType = Objects.requireNonNull(collaborationType);
    }

    /**
     * Soft-deletes the campaign while preserving ownership and application history.
     */
    public void archive(Instant now) {
        Objects.requireNonNull(now);

        if (archivedAt != null || status == CampaignStatus.ARCHIVED) {
            return;
        }

        if ((status == CampaignStatus.PUBLISHED
                || status == CampaignStatus.PAUSED)
                && closedAt == null) {
            closedAt = now;
        }

        status = CampaignStatus.ARCHIVED;
        archivedAt = now;
    }
    // Pins the exact immutable form used when the campaign was published.
// Null for Google Forms campaigns.
    @Column(name = "published_application_form_version_id")
    private UUID publishedApplicationFormVersionId;

    /**
     * Publishing is permitted only after the service validates the complete
     * campaign and resolves the application form/destination.
     */
    public void publish(Instant now, UUID formVersionId) {
        Objects.requireNonNull(now);

        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException("Only a draft can be published.");
        }

        if (applicationMode == ApplicationMode.PLATFORM
                && formVersionId == null) {
            throw new IllegalArgumentException(
                    "Platform publication requires a form version."
            );
        }

        if (applicationMode == ApplicationMode.GOOGLE_FORM
                && formVersionId != null) {
            throw new IllegalArgumentException(
                    "Google Forms publication must not pin an internal form."
            );
        }

        if (applicationMode == null) {
            throw new IllegalArgumentException(
                    "Application mode is required."
            );
        }

        publishedApplicationFormVersionId = formVersionId;
        publishedAt = now;
        status = CampaignStatus.PUBLISHED;
    }

    public void pause() {
        if (status != CampaignStatus.PUBLISHED || archivedAt != null) {
            throw new IllegalStateException(
                    "Only a published campaign can be paused."
            );
        }

        status = CampaignStatus.PAUSED;
    }

    public void resume() {
        if (status != CampaignStatus.PAUSED || archivedAt != null) {
            throw new IllegalStateException(
                    "Only a paused campaign can be resumed."
            );
        }

        // Preserve the original publication timestamp and form version.
        status = CampaignStatus.PUBLISHED;
    }

    public void close(Instant now) {
        Objects.requireNonNull(now);

        if (archivedAt != null
                || (status != CampaignStatus.PUBLISHED
                && status != CampaignStatus.PAUSED)) {
            throw new IllegalStateException(
                    "Only published or paused campaigns can be closed."
            );
        }

        closedAt = now;
        status = CampaignStatus.CLOSED;
    }


}