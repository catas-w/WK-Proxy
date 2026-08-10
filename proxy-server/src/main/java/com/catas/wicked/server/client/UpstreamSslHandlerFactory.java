package com.catas.wicked.server.client;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;

import java.net.IDN;
import java.util.Objects;

/** Creates direct-TLS handlers using the origin endpoint rather than the connected socket. */
public final class UpstreamSslHandlerFactory {

    public static final AttributeKey<String> TLS_PEER_HOST =
            AttributeKey.valueOf(UpstreamSslHandlerFactory.class, "peerHost");
    public static final AttributeKey<Integer> TLS_PEER_PORT =
            AttributeKey.valueOf(UpstreamSslHandlerFactory.class, "peerPort");
    public static final AttributeKey<String> TLS_SNI_HOST =
            AttributeKey.valueOf(UpstreamSslHandlerFactory.class, "sniHost");
    public static final AttributeKey<Long> TLS_HANDSHAKE_START_NANOS =
            AttributeKey.valueOf(UpstreamSslHandlerFactory.class, "handshakeStartNanos");

    private UpstreamSslHandlerFactory() {
    }

    public static SslHandler create(SslContext sslContext, Channel channel, String host, int port) {
        Objects.requireNonNull(sslContext, "sslContext");
        Objects.requireNonNull(channel, "channel");
        String peerHost = normalizeHost(host);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid TLS peer port: " + port);
        }

        channel.attr(TLS_PEER_HOST).set(peerHost);
        channel.attr(TLS_PEER_PORT).set(port);
        channel.attr(TLS_HANDSHAKE_START_NANOS).set(System.nanoTime());

        if (NetUtil.isValidIpV4Address(peerHost) || NetUtil.isValidIpV6Address(peerHost)) {
            return sslContext.newHandler(channel.alloc());
        }
        String sniHost = IDN.toASCII(peerHost);
        channel.attr(TLS_SNI_HOST).set(sniHost);
        return sslContext.newHandler(channel.alloc(), sniHost, port);
    }

    static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("TLS peer host is required");
        }
        String normalized = host.trim();
        if (normalized.length() > 1 && normalized.charAt(0) == '['
                && normalized.charAt(normalized.length() - 1) == ']') {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("TLS peer host is required");
        }
        return normalized;
    }
}
