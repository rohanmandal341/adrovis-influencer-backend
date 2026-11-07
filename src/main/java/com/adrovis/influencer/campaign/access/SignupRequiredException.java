package com.adrovis.influencer.campaign.access;

public class SignupRequiredException extends RuntimeException {

    public SignupRequiredException() {
        super("Create an account or log in to apply to more campaigns.");
    }
}
