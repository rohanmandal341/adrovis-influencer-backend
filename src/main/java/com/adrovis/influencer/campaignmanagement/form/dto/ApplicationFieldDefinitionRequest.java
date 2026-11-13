package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationFieldType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ApplicationFieldDefinitionRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-z][a-zA-Z0-9]{0,49}$",
                message = "Use a camelCase field key beginning with a letter."
        )
        String key,

        @NotBlank
        @Size(max = 120)
        String label,

        @NotNull
        ApplicationFieldType type,

        @NotNull
        Boolean required,

        @Size(max = 300)
        String helpText,

        @Size(max = 30)
        List<@NotBlank @Size(max = 100) String> options,

        @Min(1)
        @Max(5000)
        Integer maxLength,

        BigDecimal min,

        BigDecimal max
) {

    public ApplicationFieldDefinitionRequest {
        key = trim(key);
        label = trim(label);
        helpText = trim(helpText);

        // Preserve null elements so Bean Validation can report them.
        if (options != null) {
            options = options.stream()
                    .map(ApplicationFieldDefinitionRequest::trim)
                    .toList();
        }
    }

    /**
     * Only selection fields may declare options.
     * Options must be nonblank and unique.
     */
    @AssertTrue(message =
            "Selection fields need 1 to 30 unique options; "
                    + "other field types must not define options.")
    public boolean isOptionsConfigurationValid() {
        if (type == null) {
            return true; // @NotNull reports the missing type.
        }

        boolean selection = type == ApplicationFieldType.SINGLE_SELECT
                || type == ApplicationFieldType.MULTI_SELECT;

        if (!selection) {
            return options == null || options.isEmpty();
        }

        return options != null
                && !options.isEmpty()
                && options.size() <= 30
                && options.stream().allMatch(
                option -> option != null && !option.isBlank()
        )
                && options.stream().distinct().count() == options.size();
    }

    /**
     * Numeric bounds belong only to INTEGER/DECIMAL fields.
     * Integer fields cannot use fractional bounds.
     */
    @AssertTrue(message = "Numeric bounds are invalid for this field.")
    public boolean isNumericConfigurationValid() {
        if (type == null) {
            return true;
        }

        boolean numeric = type == ApplicationFieldType.INTEGER
                || type == ApplicationFieldType.DECIMAL;

        if (!numeric) {
            return min == null && max == null;
        }

        if (!safeNumber(min) || !safeNumber(max)) {
            return false;
        }

        if (min != null && max != null && min.compareTo(max) > 0) {
            return false;
        }

        if (type == ApplicationFieldType.INTEGER) {
            return wholeNumber(min) && wholeNumber(max);
        }

        return true;
    }

    /**
     * Length limits apply only to string-valued fields.
     * Defaults and answer validation will be enforced by the answer validator.
     */
    @AssertTrue(message =
            "Length limits are unsupported for this field type "
                    + "or exceed the permitted maximum.")
    public boolean isLengthConfigurationValid() {
        if (type == null || maxLength == null) {
            return true;
        }

        int permitted = switch (type) {
            case SHORT_TEXT -> 200;
            case LONG_TEXT -> 5000;
            case URL -> 2048;
            case EMAIL -> 254;
            case PHONE -> 32;
            default -> 0;
        };

        return maxLength >= 1 && maxLength <= permitted;
    }

    private static boolean wholeNumber(BigDecimal value) {
        return value == null || value.stripTrailingZeros().scale() <= 0;
    }

    private static boolean safeNumber(BigDecimal value) {
        if (value == null) {
            return true;
        }

        // Bound both magnitude and decimal places.
        BigDecimal normalized = value.stripTrailingZeros();

        return normalized.abs().compareTo(
                new BigDecimal("1000000000000000")
        ) <= 0 && normalized.scale() <= 6;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}