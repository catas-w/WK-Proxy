package com.catas.wicked.common.constant;

import lombok.Getter;

@Getter
public enum ProxyProtocol {
    NONE("None", -1, false),
    SYSTEM("System Proxy", -1, false),
    HTTP("HTTP", 0, true),
    SOCKS4("SOCKS4", 1, true),
    SOCKS5("SOCKS5", 2, true);

    private final String name;
    private final int ordinal;
    private final boolean active;

    ProxyProtocol(String name, int ordinal, boolean active) {
        this.name = name;
        this.ordinal = ordinal;
        this.active = active;
    }

    @Override
    public String toString() {
        return name;
    }
}
