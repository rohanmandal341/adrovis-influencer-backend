package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsDeploymentTests {
    private MockEnvironment production(String local) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("adrovis.local-development-origins", local);
        return environment;
    }

    @Test
    void productionPreflightAllowsConfiguredWebsiteAndLocalFrontend() throws Exception {
        var source = new CorsConfig().coreCorsSource(
                "https://brandcollabz.in,https://www.brandcollabz.in",
                production("http://localhost:5173"));
        for (String origin : new String[]{"https://brandcollabz.in", "https://www.brandcollabz.in", "http://localhost:5173"}) {
            for (String path : new String[]{"/api/v1/analytics/track", "/api/v1/analytics/conversion",
                    "/api/v1/newsletter/subscribe", "/api/v1/campaigns/id/apply-access"}) {
                var response = preflight(source, origin, path, "authorization,content-type");
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
                assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            }
        }
    }

    @Test
    void unconfiguredOriginsAndHeadersStayRejected() throws Exception {
        var source = new CorsConfig().coreCorsSource("https://brandcollabz.in", production("http://localhost:5173"));
        for (String origin : new String[]{"http://localhost:3000", "http://localhost:5174",
                "https://attacker.example", "http://localhost.attacker.example:5173", "null"}) {
            assertThat(preflight(source, origin, "/api/v1/analytics/track", "content-type").getStatus()).isEqualTo(403);
        }
        assertThat(preflight(source, "http://localhost:5173", "/api/v1/analytics/track", "x-unapproved").getStatus())
                .isEqualTo(403);
    }

    @Test
    void emptyDevelopmentListDisablesLocalAccess() throws Exception {
        var source = new CorsConfig().coreCorsSource("https://brandcollabz.in", production(""));
        assertThat(preflight(source, "http://localhost:5173", "/api/v1/analytics/track", "content-type").getStatus())
                .isEqualTo(403);
    }

    @Test
    void malformedOrNonLoopbackDevelopmentOriginsFailStartup() {
        for (String origin : new String[]{"*", "http://example.com:5173", "http://localhost:5173/",
                "http://localhost:5173/path", "http://localhost:5173?x=1", "http://localhost:99999",
                "https://brandcollabz.in", "http://localhost.attacker.example:5173"}) {
            assertThatThrownBy(() -> new CorsConfig().coreCorsSource("https://brandcollabz.in", production(origin)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private MockHttpServletResponse preflight(CorsConfigurationSource source, String origin, String path,
                                               String headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", headers);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
