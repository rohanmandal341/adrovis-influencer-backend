package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "campaign_title", nullable = false)
    private String campaignTitle;

    @Column(name = "campaign_type", nullable = false)
    private String campaignType;

    @Column(name = "application_form_url", nullable = false, unique = true)
    private String applicationFormUrl;

    @Column(name = "brand_instagram_url")
    private String brandInstagramUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> canonicalData;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "views_count")
    @Builder.Default
    private Long viewsCount = 0L;

    @Column(name = "clicks_count")
    @Builder.Default
    private Long clicksCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private AgencyEntity agency;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("imageOrder ASC")
    @Builder.Default
    private List<CampaignImageEntity> images = new ArrayList<>();
}
