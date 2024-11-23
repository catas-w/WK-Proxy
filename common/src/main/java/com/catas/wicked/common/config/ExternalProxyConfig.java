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
        return "127.0.0.1";
    }

    public SocketAddress getSocketAddress() {
        if (socketAddress != null) {
            return socketAddress;
        }
        setProxyAddress();
        return socketAddress;
    }
}
