package com.adrovis.influencer.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationTests {

    private final JwtDecoder decoder = JwtValidationConfig.signatureDecoder(TokenFixtures.properties());

    @Test
    void acceptsARealSignedAccessToken() {
        assertThat(decoder.decode(TokenFixtures.sign(TokenFixtures.claims())).getSubject())
                .isEqualTo(TokenFixtures.ACCOUNT);
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        rejects(TokenFixtures.claims().issuer("https://other.example.com"));
        rejects(TokenFixtures.claims().audience("another-platform"));
    }

    @Test
    void rejectsExpiredMissingAndFutureTimestamps() {
        rejects(TokenFixtures.claims().issueTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(100))));
        rejects(TokenFixtures.claims().expirationTime(null));
        rejects(TokenFixtures.claims().issueTime(Date.from(Instant.now().plusSeconds(120))));
    }

    @Test
    void rejectsOnboardingUnknownRolesAndMalformedIdentity() {
        rejects(TokenFixtures.claims().claim("token_use", "onboarding"));
        rejects(TokenFixtures.claims().claim("roles", List.of("ONBOARDING")));
        rejects(TokenFixtures.claims().claim("roles", List.of("SUPER_ADMIN")));
        rejects(TokenFixtures.claims().subject("someone-else"));
        rejects(TokenFixtures.claims().claim("ver", -1));
    }

    @Test
    void rejectsWrongKeyAlgorithmAndMalformedToken() {
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(TokenFixtures.claims(),
                TokenFixtures.keys(), JWSAlgorithm.RS256))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(TokenFixtures.claims(),
                TokenFixtures.KEYS, JWSAlgorithm.RS512))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("invalid")).isInstanceOf(JwtException.class);
    }

    private void rejects(JWTClaimsSet.Builder claims) {
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(claims))).isInstanceOf(JwtException.class);
    }
}
