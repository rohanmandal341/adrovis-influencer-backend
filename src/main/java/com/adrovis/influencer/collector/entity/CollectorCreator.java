package com.adrovis.influencer.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "creators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectorCreator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "normalized_email")
    private String normalizedEmail;

    @Column(name = "instagram_username")
    private String instagramUsername;

    @Column(name = "normalized_username")
    private String normalizedUsername;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "followers")
    private Long followers;

    @Column(name = "following")
    private Long following;

    @Column(name = "post_count")
    private Long postCount;

    @Column(name = "location")
    private String location;

    @Column(name = "niche", columnDefinition = "TEXT")
    private String niche;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "first_added_by")
    private UUID firstAddedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}