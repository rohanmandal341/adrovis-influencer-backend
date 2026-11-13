package com.adrovis.influencer.campaignmanagement.form.validation;

import java.net.URI;
import java.util.Locale;

/**
 * Validates supported public Google Forms destinations.
 * Performs no network request and follows no redirects.
 */
public final class GoogleFormUrl {

    private GoogleFormUrl() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            return false;
        }

        try {
            URI uri = URI.create(value);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();

            if (path == null) {
                return false;
            }

            if ("forms.gle".equals(host)) {
                return path.matches("/[A-Za-z0-9_-]+/?");
            }

            if ("docs.google.com".equals(host)) {
                return path.matches(
                        "/forms/(?:u/[0-9]+/)?d/(?:e/)?"
                                + "[A-Za-z0-9_-]+/viewform/?"
                );
            }

            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}