package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.impl.PaymentWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "GUEST_APPLY_TEST_DATABASE_URL", matches = ".+")
class PaymentPersistenceTests {

    private LocalContainerEntityManagerFactoryBean factory;
    private PaymentTransactionRepository payments;
    private PaymentWebhookEventRepository events;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PaymentWebhookServiceImpl webhook;

    @BeforeAll
    void setupDatabase() throws Exception {
        String url = System.getenv("GUEST_APPLY_TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/guest_apply_test")) {
            throw new IllegalStateException("Only the disposable local test database is permitted");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url, "quota_test",
                System.getenv().getOrDefault("GUEST_APPLY_TEST_DATABASE_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("com.adrovis.influencer.payment.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        payments = repositories.getRepository(PaymentTransactionRepository.class);
        events = repositories.getRepository(PaymentWebhookEventRepository.class);
        transactions = new TransactionTemplate(new JpaTransactionManager(emf));
        try (var stream = new ClassPathResource("db/manual/002_payment_account_ownership.sql").getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        RazorpayProperties properties = new RazorpayProperties();
        properties.setWebhookSecret("database-test-webhook-secret");
        webhook = new PaymentWebhookServiceImpl(events, payments, new RazorpayWebhookVerifier(properties), new ObjectMapper());
    }

    @BeforeEach
    void clearOnlyDisposablePaymentFixtures() {
        jdbc.execute("TRUNCATE public.payment_webhook_events, public.payment_transactions");
    }

    @AfterAll
    void closeDatabase() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void accountOwnershipRoundTripsThroughTheActualJpaMappingAndLockedQuery() {
        UUID owner = UUID.randomUUID();
        transactions.executeWithoutResult(status -> payments.save(payment(owner)));
        PaymentTransaction loaded = transactions.execute(status -> payments.findLockedByRazorpayOrderId("order_test").orElseThrow());
        assertThat(loaded.getAccountId()).isEqualTo(owner);
    }

    @Test
    void concurrentIdenticalSignedBodiesAreClaimedExactlyOnce() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> transactions.execute(status -> events.claim(UUID.randomUUID(), "same-body-hash",
                        "payment.captured", "{}")));
            }
            int claimed = 0;
            for (var result : executor.invokeAll(tasks)) {
                claimed += result.get();
            }
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(transactions.<Long>execute(status -> events.count())).isEqualTo(1);
    }

    @Test
    void aRejectedCaptureRollsBackBothTheEventClaimAndThePaymentUpdate() throws Exception {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        String body = WebhookSecurityTests.payload(1, "INR");
        String signature = PaymentSecurityTests.signature(body, "database-test-webhook-secret");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature)))
                .isInstanceOf(PaymentOperationException.class);
        assertThat(transactions.<Long>execute(status -> events.count())).isZero();
        assertThat(transactions.<PaymentStatus>execute(status -> payments.findByRazorpayOrderId("order_test").orElseThrow().getStatus()))
                .isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    void successfulCaptureAndRetryPreserveOneEventAndOneSuccessfulPayment() throws Exception {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        String body = WebhookSecurityTests.payload(10000, "INR");
        String signature = PaymentSecurityTests.signature(body, "database-test-webhook-secret");
        transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature));
        transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature));
        assertThat(transactions.<Long>execute(status -> events.count())).isEqualTo(1);
        assertThat(transactions.<PaymentStatus>execute(status -> payments.findByRazorpayOrderId("order_test").orElseThrow().getStatus()))
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void browserRolesCannotReadPaymentRowsEvenWhenSelectIsGranted() {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        jdbc.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_browser_probe') THEN
                    CREATE ROLE payment_browser_probe NOLOGIN;
                  END IF;
                END $$;
                GRANT USAGE ON SCHEMA public TO payment_browser_probe;
                GRANT SELECT ON public.payment_transactions TO payment_browser_probe;
                """);
        Integer visible = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET ROLE payment_browser_probe");
                try (var result = statement.executeQuery("SELECT count(*) FROM public.payment_transactions")) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
        assertThat(visible).isZero();
    }

    private PaymentTransaction payment(UUID account) {
        return PaymentTransaction.builder().accountId(account).referenceId("TEST-" + UUID.randomUUID())
                .email("test@example.com").planId("test-plan").userRole("CREATOR")
                .amount(new BigDecimal("100.00")).currency("INR").status(PaymentStatus.INITIATED)
                .razorpayOrderId("order_test").build();
    }
}
