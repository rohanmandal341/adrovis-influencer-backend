package com.adrovis.influencer.campaign.access;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestApplyCookieTests {

    @Test
    void generatesRandomIdentitiesAndStoresOnlyTheirHash() {
        GuestApplyCookie cookies = new GuestApplyCookie(true, "Lax", new MockEnvironment());
        String first = cookies.identity(null);
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(cookies.identity(null));
        assertThat(cookies.identity(first)).isEqualTo(first);
        assertThat(cookies.identity("malformed")).isNotEqualTo("malformed");
        assertThat(cookies.hash(first)).matches("[0-9a-f]{64}").isNotEqualTo(first);
    }

    @Test
    void writesAScopedSecureHttpOnlyCookie() {
        GuestApplyCookie cookies = new GuestApplyCookie(true, "None", new MockEnvironment());
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookies.write(response, cookies.identity(null));
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "Secure", "SameSite=None",
                "Path=/api/v1/campaigns", "Max-Age=2592000");
    }

    @Test
    void rejectsInsecureProductionAndCrossSiteCookies() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new GuestApplyCookie(false, "Lax", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GuestApplyCookie(false, "None", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
