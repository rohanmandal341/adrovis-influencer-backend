package com.adrovis.influencer.campaignmanagement.application.entity;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "managed_campaign_application_decisions", schema = "public")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignApplicationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 30)
    private CampaignApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 30)
    private CampaignApplicationStatus newStatus;

    @Column(name = "internal_note", updatable = false, length = 3000)
    private String internalNote;

    @Column(name = "creator_message", updatable = false, length = 2000)
    private String creatorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CampaignApplicationDecision(
            UUID applicationId,
            UUID actorAccountId,
            CampaignApplicationStatus previousStatus,
            CampaignApplicationStatus newStatus,
            String internalNote,
            String creatorMessage,
            Instant createdAt
    ) {
        this.applicationId = Objects.requireNonNull(applicationId);
        this.actorAccountId = Objects.requireNonNull(actorAccountId);
        this.previousStatus = Objects.requireNonNull(previousStatus);
        this.newStatus = Objects.requireNonNull(newStatus);
        this.internalNote = internalNote;
        this.creatorMessage = creatorMessage;
        this.createdAt = Objects.requireNonNull(createdAt);
    }
}