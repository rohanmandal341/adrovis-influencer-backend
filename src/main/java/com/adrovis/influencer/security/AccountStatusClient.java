package com.adrovis.influencer.security;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class AccountStatusClient {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusClient.class);

    private final RestClient client;
    private final AdrovisSecurityProperties properties;

    AccountStatusClient(AdrovisSecurityProperties properties) {
        this(properties, builder(properties));
    }

    AccountStatusClient(AdrovisSecurityProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    /** Uses bounded HTTPS timeouts and disables redirects so Bearer credentials cannot follow another host. */
    private static RestClient.Builder builder(AdrovisSecurityProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.statusTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.statusTimeout());
        return RestClient.builder().requestFactory(factory);
    }

    /** Validates current account status and returns the verified email. */
    String verify(Jwt jwt) {
        Map<?, ?> body;
        try {
            body = client.get().uri(properties.statusUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw rejected("auth/me returned HTTP " + status
                        + " (status URL host may not accept this token: " + properties.statusUrl().getHost() + ")");
            }
            throw unavailable("auth/me returned HTTP " + status);
        } catch (RestClientException exception) {
            throw unavailable("auth/me call failed: " + exception.getClass().getSimpleName()
                    + " - " + exception.getMessage());
        }

        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            throw unavailable("auth/me body missing or success != true: " + body);
        }
        if (!(body.get("user") instanceof Map<?, ?> user)) {
            throw unavailable("auth/me response has no 'user' object");
        }

        // Subject must match the account the status service reports.
        if (jwt.getSubject() == null || !jwt.getSubject().equals(String.valueOf(user.get("id")))) {
            throw rejected("user.id (" + user.get("id") + ") != token sub (" + jwt.getSubject() + ")");
        }

        // Accept either field name for the email-verified flag.
        Object verifiedFlag = user.containsKey("isEmailVerified")
                ? user.get("isEmailVerified") : user.get("emailVerified");
        if (!Boolean.TRUE.equals(verifiedFlag)) {
            throw rejected("email not verified (flag=" + verifiedFlag + ")");
        }

        if (!(user.get("roles") instanceof List<?> currentRoles)) {
            throw rejected("user.roles missing in auth/me response");
        }
        Set<String> statusRoles = currentRoles.stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> tokenRolesRaw = jwt.getClaimAsStringList("roles");
        Set<String> tokenRoles = tokenRolesRaw == null ? Set.of()
                : tokenRolesRaw.stream().map(role -> role.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());

        if (statusRoles.isEmpty() || !AccessTokenValidator.ROLES.containsAll(statusRoles)) {
            throw rejected("unknown or empty roles from auth/me: " + statusRoles);
        }
        if (!statusRoles.equals(tokenRoles)) {
            throw rejected("roles differ: auth/me=" + statusRoles + " token=" + tokenRoles);
        }

        if (!(user.get("email") instanceof String email) || email.isBlank()) {
            throw unavailable("user.email missing in auth/me response");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Invalid-token outcome (401). The reason is logged server-side only. */
    private JwtValidationException rejected(String reason) {
        log.warn("Account status check rejected token: {}", reason);
        return new JwtValidationException("Account credentials are no longer valid",
                List.of(new OAuth2Error("invalid_token", "Account credentials are no longer valid", null)));
    }

    /** Retryable failure. The reason is logged server-side only. */
    private JwtException unavailable(String reason) {
        log.warn("Account status check unavailable: {}", reason);
        return new JwtException("Account validation is unavailable");
    }
}