package com.adrovis.influencer.payment.security;

/** Carries only safe payment-domain messages; never wrap secrets or gateway response bodies. */
public class PaymentOperationException extends RuntimeException {

    private final int status;
    private final String code;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public PaymentOperationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** Returns the intentional HTTP status for this payment outcome. */
    public int status() {
        return status;
    }

    /** Returns the stable frontend error identifier. */
    public String code() {
        return code;
    }
}
