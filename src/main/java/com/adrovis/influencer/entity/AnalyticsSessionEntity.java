package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analytics_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSessionEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "visitor_hash", nullable = false)
    private String visitorHash;

    @Column(name = "entry_path", nullable = false)
    private String entryPath;

    @Column(name = "referrer")
    private String referrer;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "country")
    private String country;

    @Column(name = "region")
    private String region;

    @Column(name = "city")
    private String city;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "browser")
    private String browser;

    @Column(name = "is_in_app_browser")
    private Boolean isInAppBrowser;

    @Column(name = "screen_resolution")
    private String screenResolution;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "applied")
    @Builder.Default
    private Boolean applied = false;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "last_active_at", nullable = false)
    private OffsetDateTime lastActiveAt;

    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Integer durationSeconds = 0;

    @Column(name = "active_dwell_seconds", nullable = false)
    @Builder.Default
    private Integer activeDwellSeconds = 0;

    @Column(name = "page_views", nullable = false)
    @Builder.Default
    private Integer pageViews = 1;
}