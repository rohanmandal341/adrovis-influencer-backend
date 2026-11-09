package com.adrovis.influencer.payment.client;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayClientWrapper {

    private final RazorpayProperties razorpayProperties;
    private RazorpayClient client;
    /** Initializes the payment SDK and fails startup instead of continuing with a missing client. */

    @PostConstruct
    public void init() {
        try {
            this.client = new RazorpayClient(
                    razorpayProperties.getKeyId(),
                    razorpayProperties.getKeySecret()
            );
            log.info("Initialized RazorpayClient successfully.");
        } catch (RazorpayException e) {
            // Do not continue with a null SDK client or expose credentials in a provider exception.
            throw new IllegalStateException("Unable to initialize the configured payment provider");
        }
    }
    /** Creates an authenticated account-owned order after DTO and role checks; pricing contract is unchanged. */

    public Order createOrder(BigDecimal amountInInr, String receiptId) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        // Exact conversion prevents silently rounding a user-supplied amount to a different charge.
        long amountInPaise = amountInInr.multiply(BigDecimal.valueOf(100)).longValueExact();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receiptId);
        orderRequest.put("payment_capture", 1);

        return client.orders.create(orderRequest);
    }
}
