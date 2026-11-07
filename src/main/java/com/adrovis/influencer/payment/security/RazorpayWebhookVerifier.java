package com.adrovis.influencer.payment.security;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayWebhookVerifier {

    private final RazorpayProperties razorpayProperties;

    public boolean verifySignature(String payload, String expectedSignature) {
        try {
            String secret = razorpayProperties.getWebhookSecret();
            if (secret == null || secret.isBlank()) {
                return true;
            }
            return Utils.verifyWebhookSignature(payload, expectedSignature, secret);
        } catch (Exception e) {
            return false;
        }
    }
}
