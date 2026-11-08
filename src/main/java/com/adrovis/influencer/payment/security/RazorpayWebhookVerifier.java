package com.adrovis.influencer.payment.security;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayWebhookVerifier {

    private final RazorpayProperties razorpayProperties;

    /** Authenticates the exact raw body; missing secrets/signatures fail closed, never bypass verification. */
    public boolean verifySignature(String payload, String signature) {
        String secret = razorpayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank() || payload == null || signature == null
                || !signature.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }
}
