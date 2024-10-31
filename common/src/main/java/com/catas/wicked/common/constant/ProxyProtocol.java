package com.catas.wicked.common.constant;

public enum ProxyProtocol {
    NONE("None"),
    SYSTEM("System Proxy"),
    HTTP("HTTP"),
    SOCKS4("SOCKS4"),
    SOCKS5("SOCKS5");

    private final String name;

    ProxyProtocol(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
