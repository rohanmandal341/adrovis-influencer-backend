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

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getId());
            Object version = jwt.getClaim("ver");
            List<String> roles = jwt.getClaimAsStringList("roles");
            boolean valid = jwt.getAudience().contains(audience)
                    && "access".equals(jwt.getClaimAsString("token_use"))
                    && "JWT".equals(jwt.getHeaders().get("typ"))
                    && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofMinutes(30)) <= 0
                    && version instanceof Number && ((Number) version).longValue() >= 0
                    && roles != null && !roles.isEmpty() && ROLES.containsAll(roles);
            return valid ? OAuth2TokenValidatorResult.success() : invalid();
        } catch (RuntimeException exception) {
            return invalid();
        }
    }

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
    }
}
