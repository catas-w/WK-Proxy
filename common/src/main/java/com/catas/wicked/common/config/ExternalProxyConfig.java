package com.catas.wicked.common.config;

import com.catas.wicked.common.constant.ProxyProtocol;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Data
public class ExternalProxyConfig {

    private ProxyProtocol protocol = ProxyProtocol.HTTP;

    private String host = "127.0.0.1";

    // @JsonDeserialize(using = Settings.SafeIntegerDeserializer.class)
    private Integer port;

    @JsonIgnore
    private SocketAddress socketAddress;

    private String username;

    private String password;

    @Deprecated
    private boolean usingExternalProxy;

    private boolean proxyAuth;

    public ExternalProxyConfig copy() {
        ExternalProxyConfig copy = new ExternalProxyConfig();
        copy.protocol = protocol;
        copy.host = host;
        copy.port = port;
        copy.username = username;
        copy.password = password;
        copy.usingExternalProxy = usingExternalProxy;
        copy.proxyAuth = proxyAuth;
        return copy;
    }

    public void setProxyAddress(String hostname, int port) {
        socketAddress = new InetSocketAddress(hostname, port);
    }

    public void setProxyAddress() {
        setProxyAddress(host, getPort());
    }

    public Integer getPort() {
        return port == null ? 0 : port;
    }

    public String getHost() {
        return host == null ? "127.0.0.1" : host;
    }

    public void setHost(String host) {
        this.host = host;
        this.socketAddress = null;
    }

    public void setPort(Integer port) {
        this.port = port;
        this.socketAddress = null;
    }

    public SocketAddress getSocketAddress() {
        if (socketAddress != null) {
            return socketAddress;
        }
        setProxyAddress();
        return socketAddress;
    }
}
