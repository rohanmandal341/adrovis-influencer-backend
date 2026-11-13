package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import org.springframework.http.HttpStatus;

public final class CampaignVersionHeader {

    private CampaignVersionHeader() {
    }

    public static long parse(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalid();
        }

        try {
            return Long.parseLong(
                    value.substring(1, value.length() - 1)
            );
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static CampaignManagementException invalid() {
        return new CampaignManagementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERSION_HEADER",
                "If-Match must contain a quoted non-negative campaign version."
        );
    }
}