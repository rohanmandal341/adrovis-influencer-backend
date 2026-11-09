package com.adrovis.influencer.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    /** Applies one exact browser-origin policy; controller wildcards must not broaden it. */
    @Bean
    public CorsConfigurationSource coreCorsSource(
            @Value("${adrovis.frontend-origin}") String configured, Environment environment) {
        List<String> origins = new ArrayList<>();
        for (String value : configured.split(",", -1)) {
            String origin = value.trim();
            URI uri = exactOrigin(origin);
            boolean local = isLocalHttp(uri);
            if ((!"https".equals(uri.getScheme()) && !local)
                    || (environment.acceptsProfiles(Profiles.of("prod")) && local)) {
                throw new IllegalStateException("Configure exact HTTPS production frontend origins");
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        // Explicit opt-in permits local frontend testing against a deployed production backend.
        // This separate list cannot grant arbitrary HTTP hosts or wildcard access.
        String development = environment.getProperty("adrovis.local-development-origins", "");
        for (String value : development.split(",")) {
            String origin = value.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (!isLocalHttp(exactOrigin(origin))) {
                throw new IllegalStateException("Local development origins must use HTTP localhost or 127.0.0.1");
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(true);
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN", "X-Admin-Key"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setExposedHeaders(List.of("Content-Disposition", "Retry-After"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
    /** Rejects paths, wildcards and ambiguous values before registering credentialed browser access. */
    private URI exactOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || value.contains("*") || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawPath() == null || !uri.getRawPath().isEmpty()
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Not an exact origin");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Configure exact frontend origins without paths or wildcards");
        }
    }

    /** Matches explicit loopback names only; similarly named public domains are never development origins. */
    private boolean isLocalHttp(URI uri) {
        return "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    }

}
