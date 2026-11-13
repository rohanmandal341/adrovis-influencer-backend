package com.adrovis.influencer.campaignmanagement.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record SubmitCampaignApplicationRequest(

        @NotNull
        UUID formVersionId,

        @NotNull
        @Size(max = 30)
        Map<@NotBlank @Size(max = 50) String, JsonNode> answers,

        @NotNull @Valid
        Consent consent
) {

    public record Consent(
            @NotNull
            @AssertTrue(message = "Application consent is required.")
            Boolean accepted,

            @NotBlank
            @Size(max = 64)
            String noticeVersion
    ) {
    }
}