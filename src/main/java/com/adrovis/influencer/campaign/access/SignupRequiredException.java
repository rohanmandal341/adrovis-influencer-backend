package com.adrovis.influencer.campaign.access;

public class SignupRequiredException extends RuntimeException {
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public SignupRequiredException() {
        super("Create an account or log in to apply to more campaigns.");
    }
}
