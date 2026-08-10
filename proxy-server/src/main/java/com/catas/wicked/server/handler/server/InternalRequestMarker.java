package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.constant.InternalRequestOrigin;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

final class InternalRequestMarker {

    private InternalRequestMarker() {
    }

    static InternalRequestOrigin consume(HttpRequest request, SocketAddress clientAddress,
                                         String sessionToken) {
        String marker = request.headers().get(InternalRequestOrigin.HEADER_NAME);
        request.headers().remove(InternalRequestOrigin.HEADER_NAME);
        if (marker == null || !HttpMethod.CONNECT.equals(request.method()) || !isLoopback(clientAddress)) {
            return null;
        }
        for (InternalRequestOrigin origin : InternalRequestOrigin.values()) {
            if (origin.matches(marker, sessionToken)) {
                return origin;
            }
        }
        return null;
    }

    private static boolean isLoopback(SocketAddress address) {
        if (!(address instanceof InetSocketAddress socketAddress)) {
            return false;
        }
        InetAddress inetAddress = socketAddress.getAddress();
        return inetAddress != null && inetAddress.isLoopbackAddress();
    }
}
