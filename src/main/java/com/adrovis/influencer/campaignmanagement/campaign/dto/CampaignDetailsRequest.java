package com.adrovis.influencer.campaignmanagement.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public record CampaignDetailsRequest(

        @NotNull @Valid
        AdvertisedBrand advertisedBrand,

        @NotNull
        @Size(min = 1, max = 10)
        List<@NotNull @Valid CampaignImage> images,

        @NotNull
        @Size(min = 1, max = 10)
        List<@NotBlank @Size(max = 80) String> categories,

        @Valid
        Product product,

        @NotNull @Valid
        Compensation compensation,

        @NotNull @Valid
        Eligibility eligibility,

        @NotNull @Valid
        Timeline timeline,

        @NotNull
        @Size(min = 1, max = 30)
        List<@NotNull @Valid Deliverable> deliverables,

        @NotNull
        @Size(max = 50)
        List<@NotBlank @Size(max = 1000) String> guidelines,

        @Size(max = 2000)
        String adRights
) {

    public record AdvertisedBrand(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2048) String logoUrl,
            @Size(max = 2048) String website,
            @Size(max = 200) String instagramUrl
    ) {
        @AssertTrue(message = "Brand URLs must be valid HTTPS URLs.")
        public boolean isUrlConfigurationValid() {
            return optionalHttps(logoUrl)
                    && optionalHttps(website)
                    && optionalHttps(instagramUrl);
        }
    }

    public record CampaignImage(
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 200) String alt
    ) {
        @AssertTrue(message = "Image URL must use HTTPS.")
        public boolean isUrlValid() {
            return url != null && !url.isBlank() && optionalHttps(url);
        }
    }

    public record Product(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record Compensation(
            @NotBlank @Pattern(regexp = "INR") String currency,
            @NotNull Boolean productProvided,
            @DecimalMin("0.00")
            @Digits(integer = 12, fraction = 2)
            BigDecimal productValue,
            @DecimalMin("0.00")
            @Digits(integer = 12, fraction = 2)
            BigDecimal cashAmount,
            @Size(max = 2000) String description
    ) {
    }

    public record Eligibility(
            @NotNull
            @Size(min = 1, max = 3)
            List<
                    @NotBlank
                    @Pattern(regexp = "INSTAGRAM|YOUTUBE|OTHER")
                            String
                    > platforms,

            @NotNull @Min(0) @Max(1_000_000_000)
            Long minimumFollowers,

            @Min(0) @Max(1_000_000_000)
            Long maximumFollowers,

            @NotNull
            @Size(max = 4)
            List<
                    @NotBlank
                    @Pattern(regexp =
                            "MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY")
                            String
                    > genders,

            @NotNull @Valid
            Locations locations
    ) {
        @AssertTrue(message =
                "Maximum followers must not be below minimum followers.")
        public boolean isFollowerRangeValid() {
            return minimumFollowers == null
                    || maximumFollowers == null
                    || maximumFollowers >= minimumFollowers;
        }
    }

    public record Locations(
            @NotBlank
            @Pattern(regexp = "COUNTRY|SELECTED_LOCATIONS")
            String scope,

            @NotNull
            @Size(min = 1, max = 20)
            List<@Pattern(regexp = "[A-Z]{2}") String> countryCodes,

            @NotNull
            @Size(max = 100)
            List<@NotBlank @Size(max = 100) String> states,

            @NotNull
            @Size(max = 200)
            List<@NotBlank @Size(max = 100) String> cities
    ) {
        @AssertTrue(message =
                "COUNTRY must not restrict states/cities; "
                        + "SELECTED_LOCATIONS requires a state or city.")
        public boolean isScopeConfigurationValid() {
            if (scope == null || states == null || cities == null) {
                return true;
            }

            return switch (scope) {
                case "COUNTRY" -> states.isEmpty() && cities.isEmpty();
                case "SELECTED_LOCATIONS" ->
                        !states.isEmpty() || !cities.isEmpty();
                default -> false;
            };
        }
    }

    public record Timeline(
            @NotBlank
            @Pattern(regexp = "ROLLING|FIXED")
            String applicationMode,

            Instant applicationDeadline,
            Instant contentSubmissionAt,
            Instant contentLiveAt,

            @NotNull
            Instant closesAt,

            @NotBlank @Size(max = 80)
            String timezone
    ) {
        @AssertTrue(message = "Timeline dates or application deadline are invalid.")
        public boolean isDateOrderValid() {
            if (applicationMode == null || closesAt == null) {
                return true;
            }

            if ("FIXED".equals(applicationMode)
                    && applicationDeadline == null) {
                return false;
            }

            if ("ROLLING".equals(applicationMode)
                    && applicationDeadline != null) {
                return false;
            }

            if (afterClose(applicationDeadline)
                    || afterClose(contentSubmissionAt)
                    || afterClose(contentLiveAt)) {
                return false;
            }

            return contentSubmissionAt == null
                    || contentLiveAt == null
                    || !contentSubmissionAt.isAfter(contentLiveAt);
        }

        @AssertTrue(message = "Enter a supported timezone.")
        public boolean isTimezoneValid() {
            if (timezone == null || timezone.isBlank()) {
                return true;
            }

            try {
                java.time.ZoneId.of(timezone);
                return true;
            } catch (java.time.DateTimeException exception) {
                return false;
            }
        }

        private boolean afterClose(Instant value) {
            return value != null && value.isAfter(closesAt);
        }
    }

    public record Deliverable(
            @NotBlank
            @Pattern(regexp = "INSTAGRAM|YOUTUBE|OTHER")
            String platform,

            @NotBlank
            @Pattern(regexp = "REEL|STORY|POST|VIDEO|SHORT|OTHER")
            String type,

            @NotNull @Min(1) @Max(100)
            Integer quantity,

            @NotBlank @Size(max = 1000)
            String description
    ) {
    }

    private static boolean optionalHttps(String value) {
        if (value == null) {
            return true;
        }

        if (value.isBlank() || !value.equals(value.trim())) {
            return false;
        }

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
}