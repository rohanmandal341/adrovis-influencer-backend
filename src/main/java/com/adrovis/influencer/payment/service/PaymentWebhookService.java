package com.adrovis.influencer.payment.service;

public interface PaymentWebhookService {
    void processWebhook(String payload, String signature);
}
