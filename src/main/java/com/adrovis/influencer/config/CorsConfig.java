package com.adrovis.influencer.config;

import java.net.URI;
import java.util.Arrays;
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
        List<String> origins = Arrays.stream(configured.split(",")).map(String::trim).toList();
        for (String value : origins) {
            URI origin = URI.create(value);
            boolean secure = "https".equals(origin.getScheme());
            boolean local = "http".equals(origin.getScheme())
                    && List.of("localhost", "127.0.0.1").contains(origin.getHost());
            if (origin.getHost() == null || (!secure && !local) || value.contains("*")
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                    || origin.getRawFragment() != null || !origin.getRawPath().isEmpty()
                    || (environment.acceptsProfiles(Profiles.of("prod")) && !secure)) {
                throw new IllegalStateException("Configure exact frontend origins; production requires HTTPS");
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
}
