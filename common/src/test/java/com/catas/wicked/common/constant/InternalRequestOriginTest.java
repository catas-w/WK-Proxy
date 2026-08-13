package com.catas.wicked.common.constant;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InternalRequestOriginTest {

    @Test
    public void markerRequiresTheCurrentSessionToken() {
        String marker = InternalRequestOrigin.RESEND.headerValue("session-one");

        assertTrue(InternalRequestOrigin.RESEND.matches(marker, "session-one"));
        assertFalse(InternalRequestOrigin.RESEND.matches(marker, "session-two"));
        assertFalse(InternalRequestOrigin.RESEND.matches(null, "session-one"));
    }
}
