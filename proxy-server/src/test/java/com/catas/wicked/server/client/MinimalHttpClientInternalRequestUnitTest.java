package com.catas.wicked.server.client;

import com.catas.wicked.common.constant.InternalRequestOrigin;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MinimalHttpClientInternalRequestUnitTest {

    @Test
    public void regularClientDoesNotAddConnectHeaders() throws Exception {
        try (MinimalHttpClient client = MinimalHttpClient.builder().build()) {
            assertNull(client.buildConnectHeaders());
        }
    }

    @Test
    public void resendClientAddsMarkerOnlyToConnectHeaders() throws Exception {
        try (MinimalHttpClient client = MinimalHttpClient.builder()
                .internalRequest(InternalRequestOrigin.RESEND, "session-token")
                .build()) {
            HttpHeaders headers = client.buildConnectHeaders();

            assertEquals(InternalRequestOrigin.RESEND.headerValue("session-token"),
                    headers.get(InternalRequestOrigin.HEADER_NAME));
            assertEquals(1, headers.size());
        }
    }
}
