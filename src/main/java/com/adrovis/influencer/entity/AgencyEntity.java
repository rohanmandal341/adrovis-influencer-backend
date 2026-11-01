package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "contact_phone", unique = true)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "instagram_handle")
    private String instagramHandle;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "agency", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CampaignEntity> campaigns = new ArrayList<>();
}
