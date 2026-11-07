package com.adrovis.influencer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analytics_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "visitor_hash", nullable = false, length = 64)
    private String visitorHash;

    @Column(name = "entry_path", nullable = false, length = 512)
    private String entryPath;

    @Column(name = "last_path", length = 512)
    private String lastPath;

    @Column(name = "referrer", length = 1024)
    private String referrer;

    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    @Column(name = "utm_source", length = 128)
    private String utmSource;

    @Column(name = "utm_medium", length = 128)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 128)
    private String utmCampaign;

    @Column(name = "utm_content", length = 128)
    private String utmContent;

    @Column(name = "utm_term", length = 128)
    private String utmTerm;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "channel", nullable = false, length = 32)
    @Builder.Default
    private String channel = "Direct";

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "device_type", nullable = false, length = 16)
    @Builder.Default
    private String deviceType = "Desktop";

    @Column(name = "browser", length = 48)
    private String browser;

    @Column(name = "os", length = 48)
    private String os;

    @Column(name = "in_app_browser", nullable = false)
    private boolean inAppBrowser;

    @Column(name = "in_app_source", length = 32)
    private String inAppSource;

    @Column(name = "screen_resolution", length = 24)
    private String screenResolution;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "language", length = 16)
    private String language;

    @Column(name = "returning_visitor", nullable = false)
    private boolean returningVisitor;

    @Column(name = "page_views", nullable = false)
    @Builder.Default
    private int pageViews = 1;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "active_dwell_seconds", nullable = false)
    private int activeDwellSeconds;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_active_at", nullable = false)
    private OffsetDateTime lastActiveAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastActiveAt == null) lastActiveAt = now;
    }
}