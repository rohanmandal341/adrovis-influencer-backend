package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.*;
import com.adrovis.influencer.collector.controller.*;
import com.adrovis.influencer.collector.service.CollectorCreatorService;
import com.adrovis.influencer.collector.service.CollectorDashboardService;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import com.adrovis.influencer.campaign.access.CampaignApplyAccessService;
import com.adrovis.influencer.campaign.access.GuestApplyCookie;
import com.adrovis.influencer.payment.controller.*;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.PaymentService;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.repository.*;
import com.adrovis.influencer.service.AgencyService;
import com.adrovis.influencer.service.CampaignService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CampaignController.class, AgencyController.class, InquiryController.class,
        NewsletterController.class, AnalyticsController.class, CollectorCreatorController.class,
        CollectorDashboardController.class, PaymentController.class, AdminPaymentController.class,
        RazorpayWebhookController.class}, properties = {"spring.profiles.active=test",
        "adrovis.frontend-origin=https://app.example.com", "app.guest-apply.cookie-secure=false"})
@Import({SecurityConfig.class, CorsConfig.class, CurrentAccount.class, GuestApplyCookie.class})
class ApiAuthorizationMatrixTests {

    @Autowired private MockMvc mvc;
    @MockBean private JwtDecoder decoder;
    @MockBean private CampaignService campaigns;
    @MockBean private CampaignApplyAccessService apply;
    @MockBean private AgencyService agencies;
    @MockBean private InquiryRepository inquiries;
    @MockBean private NewsletterSubscriptionRepository subscribers;
    @MockBean private AnalyticsRepository analytics;
    @MockBean private CollectorCreatorService creators;
    @MockBean private CollectorDashboardService dashboard;
    @MockBean private CollectorTeamMemberRepository members;
    @MockBean private PaymentService payments;
    @MockBean private PaymentWebhookService webhooks;

    private static final UUID ID = UUID.fromString("ad9f1111-2222-4333-8444-555566667777");
    private static final List<String> ADMIN_GETS = List.of(
            "/api/v1/agencies", "/api/v1/collector/creators", "/api/v1/collector/creators/export",
            "/api/v1/collector/creators/members", "/api/v1/collector/dashboard/overview",
            "/api/v1/inquiries", "/api/v1/inquiries/" + ID, "/api/v1/newsletter/subscribers",
            "/api/v1/analytics/overview", "/api/v1/analytics/sessions", "/api/v1/analytics/sessions/" + ID,
            "/api/v1/analytics/leads", "/api/v1/admin/payments/stats", "/api/v1/admin/payments/transactions");

    @BeforeEach
    void setup() {
        for (String role : List.of("ADMIN", "CREATOR", "BRAND", "AGENCY")) {
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "RS256")
                    .subject(ID.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                    .claim("roles", List.of(role)).claim("account_email", "user@example.com").build());
        }
        when(campaigns.getFilteredCampaigns(any())).thenReturn(Page.empty());
        when(inquiries.save(any())).thenReturn(InquiryEntity.builder().id(ID).build());
        when(inquiries.findFilteredInquiries(isNull(), isNull(), isNull(), any())).thenReturn(Page.empty());
        when(inquiries.findById(any())).thenReturn(java.util.Optional.of(InquiryEntity.builder().id(ID).build()));
        when(subscribers.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        when(subscribers.findFilteredSubscriptions(any(), any(), any())).thenReturn(Page.empty());
        when(analytics.findById(any())).thenReturn(java.util.Optional.of(
                com.adrovis.influencer.entity.AnalyticsSessionEntity.builder().sessionId(ID).build()));
        when(analytics.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());
        when(analytics.upsertLead(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(creators.exportCreatorsCsv(any())).thenReturn("id,email\n");
        when(payments.createOrder(any())).thenReturn(PaymentResponse.builder().status(PaymentStatus.INITIATED).build());
        when(payments.verifyPayment(any())).thenReturn(PaymentResponse.builder().status(PaymentStatus.SUCCESS).build());
        PaymentTransaction payment = PaymentTransaction.builder().id(ID).accountId(ID)
                .razorpaySignature("not-for-api-responses").amount(BigDecimal.TEN).build();
        when(payments.getAllTransactions(any(), any())).thenReturn(new PageImpl<>(List.of(payment)));
    }

    @Test
    void everyManagementGetRequiresABearer() throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "BRAND", "AGENCY"})
    void nonAdminRolesCannotReadAnyManagementCollection(String role) throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route).header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsRemainAvailableWithoutExposingGatewaySignatures() throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route).header("Authorization", "Bearer ADMIN")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/admin/payments/transactions").header("Authorization", "Bearer ADMIN"))
                .andExpect(jsonPath("$.data.content[0].accountId").value(ID.toString()))
                .andExpect(jsonPath("$.data.content[0].razorpaySignature").doesNotExist());
    }

    @Test
    void campaignBrowsingAndItsFiltersRemainPublic() throws Exception {
        mvc.perform(get("/api/v1/campaigns").param("brand", "Example").param("location", "Mumbai")
                        .param("page", "0").param("size", "12"))
                .andExpect(status().isOk());
    }

    @Test
    void inquiriesRemainPublicButNewsletterRequiresSignup() throws Exception {
        mvc.perform(post("/api/v1/inquiries").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test","email":"guest@example.com","message":"An inquiry"}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/newsletter/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "BRAND", "AGENCY"})
    void allAccountRolesMaySubscribeOnlyTheirOwnVerifiedEmail(String role) throws Exception {
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noClientPaymentOperationCanBeCalledWithoutBearer() throws Exception {
        mvc.perform(post("/api/v1/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content(order("CREATOR"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/verify").contentType(MediaType.APPLICATION_JSON).content(verification()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "BRAND", "AGENCY"})
    void authenticatedPaymentRoutesReachTheirServices(String role) throws Exception {
        mvc.perform(post("/api/v1/payments/create-order").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content(order(role)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content(verification()))
                .andExpect(status().isOk());
    }

    @Test
    void visitorEventsStillWorkForGuestsWhileTheirReadApisStayPrivate() throws Exception {
        String session = "{\"sessionId\":\"" + ID + "\"}";
        mvc.perform(post("/api/v1/analytics/track").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"visitorId\":\"visitor\",\"sessionId\":\"" + ID + "\",\"path\":\"/campaigns\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/heartbeat").contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/conversion").contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/lead").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"sessionId\":\"" + ID + "\",\"email\":\"guest@example.com\",\"consent\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void signedProviderCallbacksUseHmacRatherThanCustomerBearer() throws Exception {
        doThrow(new PaymentOperationException(401, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid."))
                .when(webhooks).processWebhook(anyString(), eq("invalid"));
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "invalid").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wildcardPaymentCorsCannotOverrideTheCentralAllowlist() throws Exception {
        mvc.perform(options("/api/v1/payments/create-order").header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedInvalidPaymentAmountsAreRejectedBeforeTheService() throws Exception {
        for (String amount : new String[]{"0", "-1", "0.001"}) {
            mvc.perform(post("/api/v1/payments/create-order").header("Authorization", "Bearer CREATOR")
                            .contentType(MediaType.APPLICATION_JSON).content(order("CREATOR").replace("100.00", amount)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).createOrder(any());
    }

    private String order(String role) {
        return "{\"email\":\"user@example.com\",\"planId\":\"existing-plan\",\"userRole\":\"" + role
                + "\",\"amount\":100.00}";
    }

    private String verification() {
        return "{\"razorpayOrderId\":\"order_test\",\"razorpayPaymentId\":\"pay_test\","
                + "\"razorpaySignature\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}";
    }
}
