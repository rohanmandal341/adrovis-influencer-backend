package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustConfigurationTests {

    @Test
    void acceptsConfiguredPublicKeyFileAndHttpsStatusEndpoint() {
        assertThatCode(() -> properties("https://auth.example.com", "https://auth.example.com/api/v1/auth/me"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureEndpointsAndUnexpectedPaths() {
        assertThatThrownBy(() -> properties("http://auth.example.com", "https://auth.example.com/api/v1/auth/me"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties("https://auth.example.com", "https://auth.example.com/other"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties("https://auth.example.com", "http://auth.example.com/api/v1/auth/me"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRemoteKeyLoadingAndMissingTrust() throws Exception {
        assertThatThrownBy(() -> new AdrovisSecurityProperties("https://auth.example.com", "adrovis-platform",
                new UrlResource("https://untrusted.example.com/key.pem"),
                URI.create("https://auth.example.com/api/v1/auth/me"), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdrovisSecurityProperties(null, "adrovis-platform", null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresExactHttpsProductionOrigins() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        CorsConfig config = new CorsConfig();
        assertThatCode(() -> config.coreCorsSource("https://app.example.com", prod)).doesNotThrowAnyException();
        for (String value : new String[]{"*", "http://localhost:3000", "https://app.example.com/path", ""}) {
            assertThatThrownBy(() -> config.coreCorsSource(value, prod)).isInstanceOf(IllegalStateException.class);
        }
    }

    private AdrovisSecurityProperties properties(String issuer, String status) {
        return new AdrovisSecurityProperties(issuer, "adrovis-platform", new FileSystemResource("public.pem"),
                URI.create(status), Duration.ofSeconds(10));
    }
}
