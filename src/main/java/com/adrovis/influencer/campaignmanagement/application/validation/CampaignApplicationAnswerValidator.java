package com.adrovis.influencer.campaignmanagement.application.validation;

import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFieldDefinitionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignApplicationAnswerValidator {

    private static final BigDecimal MAX_NUMBER =
            new BigDecimal("1000000000000000");

    private final Validator validator;

    /**
     * Rejects unknown fields and validates values using the pinned definition.
     * Returns a normalized snapshot safe to persist.
     */
    public ObjectNode validate(
            List<ApplicationFieldDefinitionRequest> fields,
            Map<String, JsonNode> answers
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        Set<String> knownKeys = new HashSet<>();

        for (var field : fields) {
            knownKeys.add(field.key());
        }

        for (String key : answers.keySet()) {
            if (!knownKeys.contains(key)) {
                errors.put(key, "This field is not part of the application form.");
            }
        }

        for (var field : fields) {
            JsonNode value = answers.get(field.key());

            if (missing(value)) {
                if (Boolean.TRUE.equals(field.required())) {
                    errors.put(field.key(), "This field is required.");
                }
                continue;
            }

            if (!valid(field, value)) {
                errors.put(
                        field.key(),
                        "Enter a valid value matching this field's requirements."
                );
                continue;
            }

            if (value.isTextual()) {
                normalized.put(field.key(), value.textValue().trim());
            } else {
                normalized.set(field.key(), value.deepCopy());
            }
        }

        if (!errors.isEmpty()) {
            throw new ApplicationAnswersInvalidException(errors);
        }

        return normalized;
    }

    private boolean missing(JsonNode value) {
        return value == null
                || value.isNull()
                || (value.isTextual() && value.textValue().isBlank())
                || (value.isArray() && value.isEmpty());
    }

    private boolean valid(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        return switch (field.type()) {
            case SHORT_TEXT, LONG_TEXT ->
                    textWithinLimit(field, value);

            case URL ->
                    textWithinLimit(field, value)
                            && httpsUrl(value.textValue().trim());

            case EMAIL ->
                    textWithinLimit(field, value)
                            && validator.validate(
                            new EmailValue(value.textValue().trim())
                    ).isEmpty();

            case PHONE ->
                    textWithinLimit(field, value)
                            && value.textValue().trim()
                            .matches("^\\+[1-9][0-9]{7,14}$");

            case INTEGER ->
                    value.isIntegralNumber() && numeric(field, value);

            case DECIMAL ->
                    value.isNumber() && numeric(field, value);

            case BOOLEAN ->
                    value.isBoolean();

            case SINGLE_SELECT ->
                    value.isTextual()
                            && field.options().contains(value.textValue());

            case MULTI_SELECT ->
                    validMultipleSelection(field, value);
        };
    }

    private boolean textWithinLimit(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        if (!value.isTextual()) {
            return false;
        }

        int limit = field.maxLength() == null
                ? defaultLength(field.type())
                : field.maxLength();

        return value.textValue().trim().length() <= limit;
    }

    private int defaultLength(ApplicationFieldType type) {
        return switch (type) {
            case SHORT_TEXT -> 200;
            case LONG_TEXT -> 5000;
            case URL -> 2048;
            case EMAIL -> 254;
            case PHONE -> 32;
            default -> 0;
        };
    }

    private boolean numeric(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        try {
            BigDecimal number = value.decimalValue().stripTrailingZeros();

            if (number.abs().compareTo(MAX_NUMBER) > 0
                    || number.scale() > 6) {
                return false;
            }

            return (field.min() == null
                    || number.compareTo(field.min()) >= 0)
                    && (field.max() == null
                    || number.compareTo(field.max()) <= 0);
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private boolean validMultipleSelection(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        if (!value.isArray() || value.size() > field.options().size()) {
            return false;
        }

        Set<String> selected = new HashSet<>();

        for (JsonNode item : value) {
            if (!item.isTextual()
                    || !field.options().contains(item.textValue())
                    || !selected.add(item.textValue())) {
                return false;
            }
        }

        return true;
    }

    private boolean httpsUrl(String value) {
        try {
            URI uri = URI.create(value);

            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record EmailValue(@Email String value) {
    }
}