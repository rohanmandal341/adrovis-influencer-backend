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

    static final Set<String> ROLES = Set.of("CREATOR", "BRAND", "AGENCY", "ADMIN");
    private final String audience;

    AccessTokenValidator(String audience) {
        this.audience = audience;
    }
    /** Rejects onboarding tokens, malformed identity and claims outside the accepted platform contract. */

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            // UUID.fromString accepts abbreviated groups; require the full identity representation.
            if (!UUID.fromString(jwt.getSubject()).toString().equalsIgnoreCase(jwt.getSubject())
                    || !UUID.fromString(jwt.getId()).toString().equalsIgnoreCase(jwt.getId())) {
                return invalid();
            }
            Object version = jwt.getClaim("ver");
            List<String> roles = jwt.getClaimAsStringList("roles");
            boolean valid = jwt.getAudience().contains(audience)
                    && "access".equals(jwt.getClaimAsString("token_use"))
                    && "JWT".equals(jwt.getHeaders().get("typ"))
                    && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofMinutes(30)) <= 0
                    // Accept only JSON integer versions within the supported signed-long range.
                    // Number.longValue() alone silently truncates fractions and wraps oversized integers.
                    && (version instanceof Integer || version instanceof Long)
                    && ((Number) version).longValue() >= 0
                    && roles != null && !roles.isEmpty() && ROLES.containsAll(roles);
            return valid ? OAuth2TokenValidatorResult.success() : invalid();
        } catch (RuntimeException exception) {
            return invalid();
        }
    }
    /** Returns a generic token error without echoing attacker-controlled claims. */

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
    }
}
