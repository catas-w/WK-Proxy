package com.catas.wicked.common.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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

    @JsonValue
    public String getValue() {
        return name;
    }

    @JsonCreator
    public static ProxyProtocol fromValue(String value) {
        for (ProxyProtocol item : ProxyProtocol.values()) {
            if (item.getName().equalsIgnoreCase(value)) {
                return item;
            }
        }

        // default value
        return HTTP;
    }
}
