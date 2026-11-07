package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.CampaignController;
import com.adrovis.influencer.security.SecurityConfig;
import com.adrovis.influencer.service.CampaignService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CampaignController.class, properties = {
        "spring.profiles.active=test", "adrovis.frontend-origin=https://app.example.com",
        "app.guest-apply.cookie-secure=false"})
@Import({SecurityConfig.class, CorsConfig.class, GuestApplyCookie.class, GuestApplyExceptionHandler.class})
class CampaignApplyControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CampaignService campaigns;

    @MockBean
    private CampaignApplyAccessService access;

    @MockBean
    private JwtDecoder decoder;

    private final UUID id = UUID.randomUUID();
    private final String destination = "https://forms.example.com/apply";

    @Test
    void existingApplyRouteRedirectsAndIssuesGuestIdentity() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id))
                .andExpect(status().isFound()).andExpect(header().string("Location", destination))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().exists(GuestApplyCookie.NAME));
    }

    @Test
    void existingGuestIdentityIsHashedAndReused() throws Exception {
        String raw = "a".repeat(43);
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id).cookie(new Cookie(GuestApplyCookie.NAME, raw)))
                .andExpect(status().isFound()).andExpect(cookie().doesNotExist(GuestApplyCookie.NAME));
        GuestApplyCookie cookies = new GuestApplyCookie(false, "Lax", new org.springframework.mock.env.MockEnvironment());
        verify(access).grant(id, cookies.hash(raw), false);
    }

    @Test
    void bothRoutesRejectExhaustedGuestsWithoutReturningTheExternalLink() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenThrow(new SignupRequiredException());
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SIGNUP_REQUIRED"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.applicationUrl").doesNotExist());
    }

    @Test
    void jsonRouteReturnsTheLinkAndSupportsCredentialedFrontendRequests() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id).header("Origin", "https://app.example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationUrl").value(destination))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void validatedAccountsUseTheirTokenRatherThanAFrontendFlag() throws Exception {
        Jwt token = Jwt.withTokenValue("valid").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of("CREATOR")).build();
        when(decoder.decode("valid")).thenReturn(token);
        when(access.grant(eq(id), isNull(), eq(true))).thenReturn(destination);
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id).header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(cookie().doesNotExist(GuestApplyCookie.NAME));
        verify(access).grant(id, null, true);
    }

    @Test
    void invalidBearerDoesNotFallBackToGuestAccess() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid"));
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(access);
    }
    @Test
    void databaseFailuresDoNotExposeQueriesOrDestinations() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private database details"));
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("APPLICATION_ACCESS_UNAVAILABLE"))
                .andExpect(jsonPath("$.applicationUrl").doesNotExist());
    }
}
