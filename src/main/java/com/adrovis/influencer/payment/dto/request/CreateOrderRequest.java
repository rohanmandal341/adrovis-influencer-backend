package com.adrovis.influencer.payment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Plan ID is required")
    @Size(max = 50)
    private String planId;

    @NotBlank(message = "User role is required")
    @Pattern(regexp = "(?i)ADMIN|CREATOR|AGENCY|BRAND", message = "Select a supported account role")
    private String userRole;

    @NotNull(message = "Amount is required")
    // Pricing remains frontend supplied by explicit product decision; only numeric safety is enforced here.
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Amount must use at most two decimal places")
    private BigDecimal amount;
}
