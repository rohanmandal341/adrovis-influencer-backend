package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.impl.PaymentServiceImpl;
import com.adrovis.influencer.security.CurrentAccount;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentSecurityTests {

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final RazorpayClientWrapper gateway = mock(RazorpayClientWrapper.class);
    private final CurrentAccount accounts = mock(CurrentAccount.class);
    private final RazorpayProperties properties = new RazorpayProperties();
    private final UUID owner = UUID.randomUUID();
    private PaymentServiceImpl service;

    @BeforeEach
    void setup() {
        properties.setKeyId("rzp_test_not_real");
        properties.setKeySecret("test-only-signing-secret");
        when(accounts.require()).thenReturn(new CurrentAccount.Identity(owner, "user@example.com", Set.of("CREATOR")));
        service = new PaymentServiceImpl(repository, gateway, properties, accounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "AGENCY", "BRAND"})
    void ordersAreOwnedByTheAuthenticatedAccountAndPreserveFrontendPrices(String role) throws Exception {
        when(accounts.require()).thenReturn(new CurrentAccount.Identity(owner, "user@example.com", Set.of(role)));
        when(gateway.createOrder(any(), anyString())).thenReturn(new Order(new org.json.JSONObject().put("id", "order_test")));
        CreateOrderRequest request = order(role);
        service.createOrder(request);
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(owner);
        assertThat(captor.getValue().getUserRole()).isEqualTo(role);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        verify(gateway).createOrder(eq(new BigDecimal("100.00")), anyString());
    }

    @Test
    void rejectsEmailAndRoleSpoofingBeforeCallingTheGateway() {
        CreateOrderRequest email = order("CREATOR");
        email.setEmail("victim@example.com");
        assertThatThrownBy(() -> service.createOrder(email)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createOrder(order("ADMIN"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsUnsafeNumericAmountsBeforeCallingTheGateway() {
        for (String amount : new String[]{"0", "-1", "0.001"}) {
            CreateOrderRequest request = order("CREATOR");
            request.setAmount(new BigDecimal(amount));
            assertThatThrownBy(() -> service.createOrder(request)).isInstanceOf(PaymentOperationException.class);
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherUserAndUnlinkedHistoricalOrdersCannotBeVerified() {
        PaymentTransaction payment = payment();
        payment.setAccountId(UUID.randomUUID());
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> service.verifyPayment(verification("pay_test")))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(404));
        payment.setAccountId(null);
        assertThatThrownBy(() -> service.verifyPayment(verification("pay_test")))
                .isInstanceOf(PaymentOperationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void invalidSignatureCannotMarkAnOrderFailedOrSuccessful() throws Exception {
        PaymentTransaction payment = payment();
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_test");
        request.setRazorpaySignature("invalid-signature");
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(repository, never()).save(any());
    }

    @Test
    void validSignatureIsAcceptedAndIdenticalRetriesDoNotRewriteTheTransaction() throws Exception {
        PaymentTransaction payment = payment();
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_test");
        request.setRazorpaySignature(signature("order_test|pay_test", properties.getKeySecret()));
        service.verifyPayment(request);
        service.verifyPayment(request);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(repository, times(1)).save(payment);
    }

    @Test
    void aSecondPaymentCannotOverwriteSuccessAndRefundsCannotBeUndone() throws Exception {
        PaymentTransaction payment = payment();
        payment.markSuccess("pay_original", "already-verified");
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_different");
        request.setRazorpaySignature(signature("order_test|pay_different", properties.getKeySecret()));
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_original");
        payment.setStatus(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
    }

    @Test
    void providerErrorsAreNotReturnedToTheCaller() throws Exception {
        when(gateway.createOrder(any(), anyString())).thenThrow(new RazorpayException("private-provider-information"));
        assertThatThrownBy(() -> service.createOrder(order("CREATOR")))
                .isInstanceOf(PaymentOperationException.class).hasMessageNotContaining("private-provider-information")
                .hasNoCause();
    }

    private CreateOrderRequest order(String role) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEmail("user@example.com");
        request.setPlanId("existing-plan");
        request.setUserRole(role);
        request.setAmount(new BigDecimal("100.00"));
        return request;
    }

    private PaymentTransaction payment() {
        return PaymentTransaction.builder().accountId(owner).razorpayOrderId("order_test")
                .status(PaymentStatus.INITIATED).amount(new BigDecimal("100.00")).currency("INR").build();
    }

    private VerifyPaymentRequest verification(String paymentId) {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId("order_test");
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature("placeholder");
        return request;
    }

    static String signature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
