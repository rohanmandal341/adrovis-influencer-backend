package com.adrovis.influencer.payment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String fullName;

    @NotBlank(message = "Plan ID is required")
    private String planId;

    @NotBlank(message = "User role is required")
    private String userRole;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;
}
