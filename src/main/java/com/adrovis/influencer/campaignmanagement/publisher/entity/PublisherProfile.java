package com.adrovis.influencer.campaignmanagement.publisher.entity;

import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "campaign_publisher_profiles",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_publisher_owner_type",
                        columnNames = {"owner_account_id", "publisher_type"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublisherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identity comes from the validated JWT, never from signup/request input.
    // No database FK: the authentication account lives in a separate database.
    @Column(name = "owner_account_id", nullable = false, updatable = false)
    private UUID ownerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "publisher_type",
            nullable = false,
            updatable = false,
            length = 20
    )
    private PublisherType publisherType;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 3000)
    private String description;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(length = 2048)
    private String website;

    @Column(name = "instagram_url", length = 200)
    private String instagramUrl;

    @Column(length = 80)
    private String industry;

    // Contact details are private management fields by default.
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 16)
    private String contactPhone;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Used for optimistic locking when updating the profile.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Archive instead of removing campaign ownership/history.
    @Column(name = "archived_at")
    private Instant archivedAt;

    public PublisherProfile(
            UUID ownerAccountId,
            PublisherType publisherType,
            String displayName
    ) {
        this.ownerAccountId = Objects.requireNonNull(
                ownerAccountId, "Owner account is required"
        );
        this.publisherType = Objects.requireNonNull(
                publisherType, "Publisher type is required"
        );

        if (displayName == null
                || displayName.isBlank()
                || displayName.trim().length() > 120) {
            throw new IllegalArgumentException(
                    "Display name must contain 1 to 120 characters"
            );
        }

        this.displayName = displayName.trim();
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
}