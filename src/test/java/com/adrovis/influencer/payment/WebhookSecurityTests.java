package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.service.impl.PaymentWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebhookSecurityTests {

    private static final String SECRET = "test-only-webhook-secret";
    private final RazorpayProperties properties = new RazorpayProperties();
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    private RazorpayWebhookVerifier verifier;
    private PaymentWebhookServiceImpl service;

    @BeforeEach
    void setup() {
        properties.setWebhookSecret(SECRET);
        verifier = new RazorpayWebhookVerifier(properties);
        service = new PaymentWebhookServiceImpl(events, payments, verifier, new ObjectMapper());
        when(events.claim(any(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    @Test
    void onlyTheExactSignedBodyPassesAndBlankSecretsNeverBypassValidation() throws Exception {
        String signature = PaymentSecurityTests.signature(payload(10000, "INR"), SECRET);
        assertThat(verifier.verifySignature(payload(10000, "INR"), signature)).isTrue();
        assertThat(verifier.verifySignature(payload(10000, "INR") + " ", signature)).isFalse();
        assertThat(verifier.verifySignature(payload(10000, "INR"), null)).isFalse();
        properties.setWebhookSecret("");
        assertThat(verifier.verifySignature(payload(10000, "INR"), signature)).isFalse();
    }

    @Test
    void unsignedRequestsCannotClaimEventsOrModifyPayments() {
        assertThatThrownBy(() -> service.processWebhook(payload(10000, "INR"), "invalid"))
                .isInstanceOf(PaymentOperationException.class);
        verifyNoInteractions(events, payments);
    }

    @Test
    void validCapturedPaymentMustMatchStoredAmountAndCurrency() throws Exception {
        PaymentTransaction transaction = transaction();
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(payments).save(transaction);
    }

    @Test
    void signedAmountOrCurrencyMismatchDoesNotCreditAnOrder() throws Exception {
        PaymentTransaction transaction = transaction();
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        for (String body : new String[]{payload(1, "INR"), payload(10000, "USD")}) {
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class);
        }
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(payments, never()).save(any());
    }

    @Test
    void duplicateDeliveryDoesNotReprocessPayment() throws Exception {
        when(events.claim(any(), anyString(), anyString(), anyString())).thenReturn(0);
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        verifyNoInteractions(payments);
    }

    @Test
    void unknownOrdersRemainRetryableRatherThanAcknowledgedAsProcessed() throws Exception {
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.empty());
        String body = payload(10000, "INR");
        String signature = PaymentSecurityTests.signature(body, SECRET);
        assertThatThrownBy(() -> service.processWebhook(body, signature))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(503));
    }

    @Test
    void lateCapturedEventsCannotUndoRefunds() throws Exception {
        PaymentTransaction transaction = transaction();
        transaction.setStatus(PaymentStatus.REFUNDED);
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(payments, never()).save(any());
    }

    @Test
    void oversizedUnsignedBodiesAreRejectedBeforeAnyDatabaseWork() {
        assertThatThrownBy(() -> service.processWebhook("x".repeat(262145), "invalid"))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(413));
        verifyNoInteractions(events, payments);
    }

    @Test
    void signedMalformedJsonAndMissingEventNeverReachPersistence() throws Exception {
        for (String body : new String[]{"{", "{}", "null", "[]"}) {
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class)
                    .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(400));
        }
        verifyNoInteractions(events, payments);
    }

    @Test
    void signedNumericOrOversizedIdentifiersAreRejectedBeforeOrderLookup() throws Exception {
        for (String value : new String[]{"123", "\"" + "x".repeat(101) + "\""}) {
            String body = payload(10000, "INR").replace("\"order_test\"", value);
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class)
                    .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(400));
        }
        verifyNoInteractions(payments);
    }

    private PaymentTransaction transaction() {
        return PaymentTransaction.builder().razorpayOrderId("order_test").status(PaymentStatus.INITIATED)
                .amount(new BigDecimal("100.00")).currency("INR").build();
    }

    static String payload(long paise, String currency) {
        return "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"order_id\":\"order_test\",\"id\":\"pay_test\",\"amount\":" + paise
                + ",\"currency\":\"" + currency + "\"}}}}";
    }
}
