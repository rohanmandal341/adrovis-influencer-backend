package com.adrovis.influencer.campaignmanagement.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

public record CreateApplicationFormVersionRequest(

        @NotNull
        @Size(min = 1, max = 30)
        List<@NotNull @Valid ApplicationFieldDefinitionRequest> fields
) {

    public CreateApplicationFormVersionRequest {
        if (fields != null) {
            fields = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<>(fields)
            );
        }
    }

    @AssertTrue(message = "Application field keys must be unique.")
    public boolean isFieldKeysUnique() {
        if (fields == null) {
            return true;
        }

        List<String> keys = fields.stream()
                .filter(Objects::nonNull)
                .map(ApplicationFieldDefinitionRequest::key)
                .filter(Objects::nonNull)
                .toList();

        return keys.stream().distinct().count() == keys.size();
    }
}