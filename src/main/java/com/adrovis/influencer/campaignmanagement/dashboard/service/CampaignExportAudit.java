package com.adrovis.influencer.campaignmanagement.dashboard.service;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CampaignExportAudit {

    private final JdbcTemplate jdbc;

    public CampaignExportAudit(
            @Qualifier("primaryDataSource") DataSource dataSource
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Uses the primary transaction's datasource.
     * Does not store exported answers or raw search text.
     */
    public void record(
            UUID actorId,
            UUID campaignId,
            CampaignApplicationStatus status,
            boolean searchUsed,
            int rowCount,
            int fileBytes
    ) {
        jdbc.update("""
                INSERT INTO public.campaign_applicant_export_audit (
                    id,
                    actor_account_id,
                    campaign_id,
                    status_filter,
                    search_used,
                    row_count,
                    file_bytes,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                actorId,
                campaignId,
                status == null ? null : status.name(),
                searchUsed,
                rowCount,
                fileBytes,
                Timestamp.from(Instant.now())
        );
    }
}