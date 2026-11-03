package com.adrovis.influencer.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "creator_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectorSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "team_member_id", nullable = false)
    private UUID teamMemberId;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "status")
    private String status; // "new", "duplicate", "email_not_found"

    @Column(name = "extracted_username")
    private String extractedUsername;

    @Column(name = "extracted_email")
    private String extractedEmail;

    @Column(name = "extracted_data", columnDefinition = "TEXT")
    private String extractedData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}