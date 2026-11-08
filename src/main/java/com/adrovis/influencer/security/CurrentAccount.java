package com.adrovis.influencer.security;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Reads identity exclusively from the JWT validated and enriched by the account-service check. */
@Component
public class CurrentAccount {

    /** Supplies trusted identity to business services; request bodies never establish ownership. */
    public Identity require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("An account access token is required");
        }
        String email = jwt.getToken().getClaimAsString("account_email");
        if (email == null || email.isBlank()) {
            throw new AuthenticationServiceException("Verified account metadata is unavailable");
        }
        Set<String> roles = jwt.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Identity(UUID.fromString(jwt.getToken().getSubject()), email.trim().toLowerCase(Locale.ROOT), roles);
    }

    /** Account UUID is the ownership key; verified email is used only for contact/consent checks. */
    public record Identity(UUID id, String email, Set<String> roles) { }
}
