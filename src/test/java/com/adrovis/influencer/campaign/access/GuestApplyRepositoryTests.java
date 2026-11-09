package com.adrovis.influencer.campaign.access;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "GUEST_APPLY_TEST_DATABASE_URL", matches = ".+")
class GuestApplyRepositoryTests {

    private GuestApplyRepository repository;
    private JdbcTemplate jdbc;
    private final List<UUID> campaigns = new ArrayList<>();
    private final String guest = "a".repeat(64);

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("GUEST_APPLY_TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/guest_apply_test")) {
            throw new IllegalStateException("Use only the disposable local guest_apply_test database");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url, "quota_test",
                System.getenv().getOrDefault("GUEST_APPLY_TEST_DATABASE_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.campaigns (id uuid PRIMARY KEY)");
        String sql;
        try (var stream = new ClassPathResource("db/manual/001_guest_campaign_allowance.sql").getInputStream()) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute(sql);
        try (var stream = new ClassPathResource("db/manual/003_guest_apply_click_counter.sql").getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        jdbc.execute("TRUNCATE public.guest_campaign_access, public.guest_apply_sessions, public.campaigns CASCADE");
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            campaigns.add(id);
            jdbc.update("INSERT INTO public.campaigns VALUES (?)", id);
        }
        repository = new GuestApplyRepository(source, new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
    }

    @Test
    void allowsThreeDistinctCampaignsRejectsTheFourthAndPreservesRetries() {
        for (int i = 0; i < 3; i++) {
            assertThat(allow(guest, campaigns.get(i), 3)).isTrue();
        }
        assertThat(allow(guest, campaigns.get(3), 3)).isFalse();
        assertThat(allow(guest, campaigns.get(0), 3)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(3);
    }

    @Test
    void simultaneousDifferentCampaignsCannotExceedTheAllowance() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = campaigns.stream()
                    .<Callable<Boolean>>map(id -> () -> allow(guest, id, 3)).toList();
            int allowed = 0;
            for (var future : executor.invokeAll(tasks)) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(3);
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(3);
    }

    @Test
    void simultaneousRetriesConsumeOnlyOneSlot() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> allow(guest, campaigns.get(0), 3));
            }
            for (var future : executor.invokeAll(tasks)) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(1);
    }

    @Test
    void eachGuestGetsTheirOwnAllowance() {
        for (int i = 0; i < 3; i++) {
            assertThat(allow(guest, campaigns.get(i), 3)).isTrue();
        }
        assertThat(allow("b".repeat(64), campaigns.get(3), 3)).isTrue();
    }

    @Test
    void failedGrantsRollBackTheSessionAndUsage() {
        assertThatThrownBy(() -> allow(guest, UUID.randomUUID(), 3))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_apply_sessions", Integer.class)).isZero();
    }
    /** Existing distinct-campaign assertions remain to verify the selectable compatibility mode. */
    private boolean allow(String hash, UUID id, int limit) {
        return repository.allow(hash, id, limit, GuestApplyCountingMode.DISTINCT_CAMPAIGNS);
    }

    @Test
    void clickModeCountsRepeatedCampaignsAndHonorsFiveAsAConfiguredLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(repository.allow(guest, campaigns.get(0), 5, GuestApplyCountingMode.CLICKS)).isTrue();
        }
        assertThat(repository.allow(guest, campaigns.get(0), 5, GuestApplyCountingMode.CLICKS)).isFalse();
        assertThat(jdbc.queryForObject("SELECT apply_count FROM public.guest_apply_sessions", Integer.class)).isEqualTo(5);
    }

    @Test
    void simultaneousClickRequestsCannotExceedThreeEvenOnTheSameCampaign() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> repository.allow(guest, campaigns.get(0), 3, GuestApplyCountingMode.CLICKS));
            }
            int granted = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get()) {
                    granted++;
                }
            }
            assertThat(granted).isEqualTo(3);
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
    }
}
