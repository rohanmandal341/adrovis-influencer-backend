package com.adrovis.influencer.campaignmanagement.application.exception;

import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApplicationAnswersInvalidException
        extends CampaignManagementException {

    private final Map<String, String> fieldErrors;

    public ApplicationAnswersInvalidException(
            Map<String, String> fieldErrors
    ) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "APPLICATION_VALIDATION_FAILED",
                "Please correct the application fields."
        );

        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}