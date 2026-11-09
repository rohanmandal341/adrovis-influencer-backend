package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.HealthController;
import com.adrovis.influencer.controller.InquiryController;
import com.adrovis.influencer.controller.NewsletterController;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.repository.InquiryRepository;
import com.adrovis.influencer.repository.NewsletterSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, InquiryController.class, NewsletterController.class,
        AccountConnectionController.class}, properties = {"adrovis.frontend-origin=https://app.example.com",
        "spring.profiles.active=test"})
@Import({SecurityConfig.class, CorsConfig.class, CurrentAccount.class})
class RouteSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder decoder;

    @MockBean
    private InquiryRepository inquiries;

    @MockBean
    private NewsletterSubscriptionRepository subscribers;

    @BeforeEach
    void setup() {
        when(decoder.decode("creator-token")).thenReturn(jwt("CREATOR"));
        when(decoder.decode("admin-token")).thenReturn(jwt("ADMIN"));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid"));
        when(decoder.decode("unavailable-token")).thenThrow(new JwtException("Unavailable"));
    }

    @Test
    void leavesPublicHealthAccessible() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void rejectsAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void returnsIdentityFromTheTokenRatherThanRequestParameters() throws Exception {
        mvc.perform(get("/api/v1/account/me").param("accountId", "attacker")
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(TokenFixtures.ACCOUNT))
                .andExpect(jsonPath("$.roles[0]").value("CREATOR"));
    }

    @Test
    void normalUsersCannotReadInternalCollections() throws Exception {
        for (String path : List.of("/api/v1/inquiries", "/api/v1/newsletter/subscribers",
                "/api/v1/collector/creators", "/api/v1/collector/creators/export", "/api/v1/analytics/overview")) {
            mvc.perform(get(path).header("Authorization", "Bearer creator-token"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anAdminCanReadInquiries() throws Exception {
        when(inquiries.findFilteredInquiries(isNull(), isNull(), isNull(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/inquiries").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void theOldAdminKeyDoesNotBypassAuthentication() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").header("X-Admin-Key", "legacy-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preservesPublicInquirySubmission() throws Exception {
        when(inquiries.save(any())).thenReturn(InquiryEntity.builder().id(UUID.randomUUID()).build());
        mvc.perform(post("/api/v1/inquiries").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test User","email":"user@example.com","message":"A campaign inquiry"}
                """)).andExpect(status().isCreated());
    }

    @Test
    void requiresSignupForNewsletterSubscription() throws Exception {
        when(subscribers.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        mvc.perform(post("/api/v1/newsletter/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer creator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsOnlyApprovedOriginsIncludingOnActualWildcardAnnotatedControllers() throws Exception {
        mvc.perform(options("/api/v1/inquiries").header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
        mvc.perform(options("/api/v1/inquiries").header("Origin", "https://attacker.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inquiries").header("Origin", "https://attacker.example.com")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsServiceUnavailableWhenAccountValidationCannotComplete() throws Exception {
        mvc.perform(get("/api/v1/account/me").header("Authorization", "Bearer unavailable-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_UNAVAILABLE"));
    }

    @Test
    void browserRoleHeadersAndQueryTokensCannotGrantAdminAccess() throws Exception {
        mvc.perform(get("/api/v1/inquiries").header("X-Role", "ADMIN")
                        .param("access_token", "admin-token").param("role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inquiries").header("Authorization", "Bearer creator-token")
                        .header("X-Role", "ADMIN").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicReadRoutesDoNotAllowMutationOrMethodOverride() throws Exception {
        mvc.perform(post("/api/v1/campaigns").header("Authorization", "Bearer creator-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/inquiries/export").header("Authorization", "Bearer creator-token")
                        .header("X-HTTP-Method-Override", "GET"))
                .andExpect(status().isForbidden());
    }

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").subject(TokenFixtures.ACCOUNT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(role)).claim("token_use", "access")
                .claim("account_email", "user@example.com").build();
    }
}
