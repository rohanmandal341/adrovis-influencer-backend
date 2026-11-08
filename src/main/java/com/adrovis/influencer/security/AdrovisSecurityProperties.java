package com.adrovis.influencer.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("adrovis.auth")
public record AdrovisSecurityProperties(String issuer, String audience, Resource publicKeyLocation,
                                        URI statusUrl, Duration statusTimeout) {

    public AdrovisSecurityProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || publicKeyLocation == null || statusUrl == null) {
            throw new IllegalStateException("Configure Adrovis issuer, audience, public key and status URL");
        }
        URI issuerUri = URI.create(issuer);
        requireHttps(issuerUri);
        requireHttps(statusUrl);
        if (!"/api/v1/auth/me".equals(statusUrl.getPath())) {
            throw new IllegalStateException("The account status URL must point to /api/v1/auth/me");
        }
        if (statusTimeout == null) {
            statusTimeout = Duration.ofSeconds(10);
        }
        if (statusTimeout.isNegative() || statusTimeout.isZero() || statusTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException("Account status timeout must be greater than zero and at most 30 seconds");
        }
        if (!(publicKeyLocation instanceof org.springframework.core.io.ByteArrayResource)
                && !(publicKeyLocation instanceof org.springframework.core.io.ClassPathResource)
                && !publicKeyLocation.getDescription().startsWith("URL [file:")
                && !(publicKeyLocation instanceof org.springframework.core.io.FileSystemResource)) {
            throw new IllegalStateException("Load the trusted public key from a local resource, not a remote URL");
        }
    }
    /** Rejects insecure endpoints and credential-bearing or ambiguous URLs. */

    private static void requireHttps(URI uri) {
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Adrovis endpoints must use HTTPS without credentials, query or fragment");
        }
    }
}
