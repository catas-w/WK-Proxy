package com.catas.wicked.common.constant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Origins that may be asserted by a trusted in-process proxy client. */
public enum InternalRequestOrigin {
    RESEND;

    public static final String HEADER_NAME = "X-Wizard-Proxy-Internal-Origin";

    public String headerValue(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new IllegalArgumentException("sessionToken cannot be blank");
        }
        return name().toLowerCase(Locale.ROOT) + ":" + sessionToken;
    }

    public boolean matches(String headerValue, String sessionToken) {
        if (headerValue == null || sessionToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                headerValue(sessionToken).getBytes(StandardCharsets.UTF_8),
                headerValue.getBytes(StandardCharsets.UTF_8));
    }
}
