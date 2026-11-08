package com.adrovis.influencer.security;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    /** Validates current status and returns verified contact metadata without retaining the raw response. */
    String verify(Jwt jwt) {
        Map<?, ?> body;
        try {
            body = client.get().uri(properties.statusUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw rejected();
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
        if (body == null || !Boolean.TRUE.equals(body.get("success"))
                || !(body.get("user") instanceof Map<?, ?> user)) {
            throw unavailable();
        }
        if (!jwt.getSubject().equals(user.get("id")) || !Boolean.TRUE.equals(user.get("isEmailVerified"))
                || !(user.get("roles") instanceof List<?> currentRoles)) {
            throw rejected();
        }
        Set<String> roles = currentRoles.stream().filter(String.class::isInstance).map(String.class::cast)
                .map(role -> role.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (roles.size() != currentRoles.size() || !AccessTokenValidator.ROLES.containsAll(roles)
                || !roles.equals(Set.copyOf(jwt.getClaimAsStringList("roles")))) {
            throw rejected();
        }
        if (!(user.get("email") instanceof String email) || email.isBlank()) {
            throw unavailable();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
    /** Builds a safe invalid-token outcome without returning the provider response. */

    private JwtValidationException rejected() {
        return new JwtValidationException("Account credentials are no longer valid",
                List.of(new OAuth2Error("invalid_token", "Account credentials are no longer valid", null)));
    }
    /** Returns a retryable failure without bypassing account or data checks. */

    private JwtException unavailable() {
        return new JwtException("Account validation is unavailable");
    }
}
