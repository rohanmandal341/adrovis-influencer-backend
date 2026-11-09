package com.adrovis.influencer.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccessTokenValidator.class);
    static final Set<String> ROLES = Set.of("CREATOR", "BRAND", "AGENCY", "ADMIN");
    private final String audience;

    AccessTokenValidator(String audience) { this.audience = audience; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String reason = check(jwt);
        if (reason == null) return OAuth2TokenValidatorResult.success();
        log.warn("Access token rejected: {}", reason);
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
    }

    private String check(Jwt jwt) {
        try {
            String sub = jwt.getSubject();
            if (sub == null || !UUID.fromString(sub).toString().equalsIgnoreCase(sub)) return "sub is not a full UUID: " + sub;
            if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)) return "aud mismatch: " + jwt.getAudience();
            String use = jwt.getClaimAsString("token_use");
            if (use != null && !"access".equals(use)) return "token_use=" + use;   // blocks onboarding tokens if you issue the claim
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) return "missing iat/exp";
            if (Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofMinutes(30)) > 0)
                return "lifetime over 30 min";
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null || roles.isEmpty()) return "roles claim missing";
            if (!ROLES.containsAll(roles)) return "unknown roles: " + roles;
            return null;
        } catch (RuntimeException e) {
            return "malformed claims: " + e.getMessage();
        }
    }
}