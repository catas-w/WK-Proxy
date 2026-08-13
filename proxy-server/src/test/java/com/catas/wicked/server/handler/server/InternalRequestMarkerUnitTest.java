package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.constant.InternalRequestOrigin;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class InternalRequestMarkerUnitTest {

    @Test
    public void acceptsValidLoopbackConnectAndRemovesMarker() {
        HttpRequest request = request(HttpMethod.CONNECT, "token");

        InternalRequestOrigin origin = InternalRequestMarker.consume(
                request, new InetSocketAddress("127.0.0.1", 41000), "token");

        assertEquals(InternalRequestOrigin.RESEND, origin);
        assertFalse(request.headers().contains(InternalRequestOrigin.HEADER_NAME));
    }

    @Test
    public void rejectsInvalidTokenAndStillRemovesMarker() {
        HttpRequest request = request(HttpMethod.CONNECT, "old-token");

        InternalRequestOrigin origin = InternalRequestMarker.consume(
                request, new InetSocketAddress("127.0.0.1", 41000), "current-token");

        assertNull(origin);
        assertFalse(request.headers().contains(InternalRequestOrigin.HEADER_NAME));
    }

    @Test
    public void rejectsNonLoopbackAndNonConnectRequests() {
        HttpRequest remote = request(HttpMethod.CONNECT, "token");
        HttpRequest get = request(HttpMethod.GET, "token");

        assertNull(InternalRequestMarker.consume(
                remote, new InetSocketAddress("192.0.2.10", 41000), "token"));
        assertNull(InternalRequestMarker.consume(
                get, new InetSocketAddress("127.0.0.1", 41000), "token"));
        assertFalse(remote.headers().contains(InternalRequestOrigin.HEADER_NAME));
        assertFalse(get.headers().contains(InternalRequestOrigin.HEADER_NAME));
    }

    private HttpRequest request(HttpMethod method, String token) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, "example.com:443");
        request.headers().set(InternalRequestOrigin.HEADER_NAME,
                InternalRequestOrigin.RESEND.headerValue(token));
        return request;
    }
}
