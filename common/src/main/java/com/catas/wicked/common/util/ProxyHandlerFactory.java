package com.catas.wicked.common.util;

import com.catas.wicked.common.config.ExternalProxyConfig;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Optional;


/**
 * To create external proxy handler for http-clients
 */
public class ProxyHandlerFactory {

    /**
     * get netty proxyHandler by external proxy config
     * @param proxyConfig defines proxy type, host & port
     * @param url used in getting system proxy info
     * @return ProxyHandler
     */
    public static ProxyHandler getExternalProxyHandler(ExternalProxyConfig proxyConfig, String url) {
        return getExternalProxyHandler(proxyConfig, url, null);
    }

    /**
     * Creates a proxy handler and optionally adds headers to the HTTP CONNECT handshake.
     * The headers are ignored for SOCKS proxies.
     */
    public static ProxyHandler getExternalProxyHandler(ExternalProxyConfig proxyConfig, String url,
                                                        HttpHeaders connectHeaders) {
        if (proxyConfig != null) {
            switch (proxyConfig.getProtocol()) {
                case HTTP -> {
                    HttpProxyHandler httpProxyHandler = null;
                    if (proxyConfig.isProxyAuth()) {
                        String username = Optional.ofNullable(proxyConfig.getUsername()).orElse("");
                        String password = Optional.ofNullable(proxyConfig.getPassword()).orElse("");
                        httpProxyHandler = connectHeaders == null
                                ? new HttpProxyHandler(proxyConfig.getSocketAddress(), username, password)
                                : new HttpProxyHandler(proxyConfig.getSocketAddress(), username, password,
                                        connectHeaders);
                    } else {
                        httpProxyHandler = connectHeaders == null
                                ? new HttpProxyHandler(proxyConfig.getSocketAddress())
                                : new HttpProxyHandler(proxyConfig.getSocketAddress(), connectHeaders);
                    }
                    return httpProxyHandler;
                }
                case SOCKS4 -> {
                    Socks4ProxyHandler socks4ProxyHandler = null;
                    if (proxyConfig.isProxyAuth()) {
                        socks4ProxyHandler = new Socks4ProxyHandler(
                                proxyConfig.getSocketAddress(),
                                proxyConfig.getUsername());
                    } else {
                        socks4ProxyHandler = new Socks4ProxyHandler(proxyConfig.getSocketAddress());
                    }
                    return socks4ProxyHandler;
                }
                case SOCKS5 -> {
                    Socks5ProxyHandler socks5ProxyHandler = null;
                    if (proxyConfig.isProxyAuth()) {
                        socks5ProxyHandler = new Socks5ProxyHandler(
                                proxyConfig.getSocketAddress(),
                                proxyConfig.getUsername(),
                                proxyConfig.getPassword());
                    } else {
                        socks5ProxyHandler = new Socks5ProxyHandler(proxyConfig.getSocketAddress());
                    }
                    return socks5ProxyHandler;
                }
                case SYSTEM -> {
                    ExternalProxyConfig systemProxy = WebUtils.getSystemProxy(url);
                    return getExternalProxyHandler(systemProxy, url, connectHeaders);
                }
            }
        }
        return null;
    }
}
