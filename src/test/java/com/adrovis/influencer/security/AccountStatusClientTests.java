package com.adrovis.influencer.security;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountStatusClientTests {

    private MockRestServiceServer server;
    private AccountStatusClient client;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountStatusClient(TokenFixtures.properties(), builder);
        jwt = JwtValidationConfig.signatureDecoder(TokenFixtures.properties())
                .decode(TokenFixtures.sign(TokenFixtures.claims()));
    }

    @Test
    void forwardsOnlyTheValidatedBearerToTheConfiguredEndpoint() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andExpect(header("Authorization", "Bearer " + jwt.getTokenValue()))
                .andRespond(withSuccess(body(TokenFixtures.ACCOUNT, "creator"), MediaType.APPLICATION_JSON));
        assertThatCode(() -> client.verify(jwt)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void rejectsRevokedAndSuspendedCredentials() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAnAccountMismatch() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess(body("different-user", "creator"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsChangedAuthoritiesInsteadOfUsingStaleAdminClaims() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess(body(TokenFixtures.ACCOUNT, "brand"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void failsClosedOnNetworkAndProviderFailures() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withException(new IOException("private transport details")));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class)
                .hasMessage("Account validation is unavailable").hasNoCause();
    }

    @Test
    void rejectsMalformedResponses() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsRedirectsWithoutForwardingCredentialsToAnotherHost() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://attacker.example.com"));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class);
        server.verify();
    }

    @Test
    void rejectsForbiddenResponses() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    private String body(String id, String role) {
        return "{\"success\":true,\"user\":{\"id\":\"" + id
                + "\",\"isEmailVerified\":true,\"roles\":[\"" + role + "\"]}}";
    }
}
