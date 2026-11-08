package com.adrovis.influencer.campaign.access;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Repository
public class GuestApplyRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public GuestApplyRepository(@Qualifier("primaryDataSource") DataSource dataSource,
                                @Qualifier("primaryTransactionManager") PlatformTransactionManager manager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(manager);
    }

    /** Serializes quota and grant writes on one guest row; the caller's primary transaction also records the click. */
    public boolean allow(String guestHash, UUID campaignId, int limit, GuestApplyCountingMode mode) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            jdbc.update("INSERT INTO public.guest_apply_sessions (guest_hash) VALUES (?) ON CONFLICT DO NOTHING",
                    guestHash);
            // Serialize all quota decisions for this guest, including requests for different campaigns.
            Integer clicks = jdbc.queryForObject(
                    "SELECT apply_count FROM public.guest_apply_sessions WHERE guest_hash = ? FOR UPDATE",
                    Integer.class, guestHash);
            if (mode == GuestApplyCountingMode.CLICKS && clicks != null && clicks >= limit) {
                return false;
            }
            Boolean repeat = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM public.guest_campaign_access
                    WHERE guest_hash = ? AND campaign_id = ?)
                    """, Boolean.class, guestHash, campaignId);
            if (mode == GuestApplyCountingMode.DISTINCT_CAMPAIGNS && Boolean.TRUE.equals(repeat)) {
                jdbc.update("UPDATE public.guest_apply_sessions SET apply_count = apply_count + 1 WHERE guest_hash = ?",
                        guestHash);
                return true;
            }
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM public.guest_campaign_access WHERE guest_hash = ?", Integer.class, guestHash);
            if (mode == GuestApplyCountingMode.DISTINCT_CAMPAIGNS && count != null && count >= limit) {
                return false;
            }
            jdbc.update("INSERT INTO public.guest_campaign_access (guest_hash, campaign_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    guestHash, campaignId);
            jdbc.update("UPDATE public.guest_apply_sessions SET apply_count = apply_count + 1 WHERE guest_hash = ?",
                    guestHash);
            return true;
        }));
    }
}
