package com.adrovis.influencer.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyPaymentRequest {
    @NotBlank(message = "Razorpay order ID is required")
    @Size(max = 100)
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment ID is required")
    @Size(max = 100)
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay signature is required")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "Payment signature must be a SHA-256 hexadecimal value")
    private String razorpaySignature;
}
