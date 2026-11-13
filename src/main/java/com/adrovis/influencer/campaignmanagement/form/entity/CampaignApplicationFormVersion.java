package com.adrovis.influencer.campaignmanagement.form.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "campaign_application_form_versions",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_campaign_application_form_version",
                columnNames = {"campaign_id", "version_number"}
        )
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignApplicationFormVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "campaign_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_application_form_campaign")
    )
    private ManagedCampaign campaign;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode definition;

    @Column(
            name = "created_by_account_id",
            nullable = false,
            updatable = false
    )
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Called only after service-layer authorization and form validation.
     * Version numbers are allocated by the backend, not request input.
     */
    public CampaignApplicationFormVersion(
            ManagedCampaign campaign,
            int versionNumber,
            JsonNode definition,
            UUID createdByAccountId
    ) {
        this.campaign = Objects.requireNonNull(campaign);
        Objects.requireNonNull(campaign.getId(), "Campaign must already exist.");

        if (versionNumber < 1) {
            throw new IllegalArgumentException(
                    "Form version must be positive."
            );
        }

        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException(
                    "Form definition must be a JSON object."
            );
        }

        this.versionNumber = versionNumber;
        this.definition = definition.deepCopy();
        this.createdByAccountId = Objects.requireNonNull(
                createdByAccountId
        );
    }

    // Never expose the mutable JSON object stored inside the entity.
    public JsonNode getDefinition() {
        return definition == null ? null : definition.deepCopy();
    }

    @PrePersist
    private void initializeTimestamp() {
        createdAt = Instant.now();
    }
}