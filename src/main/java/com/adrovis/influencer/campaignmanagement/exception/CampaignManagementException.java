package com.adrovis.influencer.campaignmanagement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;

@Getter
public class CampaignManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public CampaignManagementException(
            HttpStatus status,
            String errorCode,
            String message
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }


}