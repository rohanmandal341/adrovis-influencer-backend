package com.adrovis.influencer.campaign.access;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class GuestApplyCookie {

    public static final String NAME = "adrovis_guest_apply";
    private final SecureRandom random = new SecureRandom();
    private final boolean secure;
    private final String sameSite;

    public GuestApplyCookie(@Value("${app.guest-apply.cookie-secure:true}") boolean secure,
                            @Value("${app.guest-apply.cookie-same-site:Lax}") String sameSite,
                            Environment environment) {
        if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite)
                || ("None".equals(sameSite) && !secure)
                || (environment.acceptsProfiles(Profiles.of("prod")) && !secure)) {
            throw new IllegalStateException("Guest cookies require a valid SameSite value and Secure in production");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String identity(String raw) {
        if (raw != null && raw.matches("[A-Za-z0-9_-]{43}")) {
            return raw;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public void write(HttpServletResponse response, String raw) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, raw)
                .httpOnly(true).secure(secure).sameSite(sameSite).path("/api/v1/campaigns")
                .maxAge(Duration.ofDays(30)).build().toString());
    }
}
