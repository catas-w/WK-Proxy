package com.catas.wicked.common.config;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class SettingsCopyTest {

    @Test
    public void copyIsDeepAndKeepsExternalProxyValues() {
        Settings source = new Settings();
        source.setRecordExcludeList(List.of("example.com"));
        source.getExternalProxy().setHost("proxy.example.com");
        source.getExternalProxy().setPort(8080);
        source.getExternalProxy().setUsername("user");
        source.getExternalProxy().setPassword("secret");

        Settings copy = source.copy();
        copy.setRecordExcludeList(List.of("changed.example.com"));
        copy.getExternalProxy().setHost("other.example.com");
        copy.getExternalProxy().setPassword("changed");

        assertNotSame(source, copy);
        assertNotSame(source.getExternalProxy(), copy.getExternalProxy());
        assertEquals(List.of("example.com"), source.getRecordExcludeList());
        assertEquals("proxy.example.com", source.getExternalProxy().getHost());
        assertEquals("secret", source.getExternalProxy().getPassword());
    }

    @Test
    public void externalProxyAddressIsInvalidatedWhenHostOrPortChanges() {
        ExternalProxyConfig config = new ExternalProxyConfig();
        config.setHost("first.example.com");
        config.setPort(8080);
        Object first = config.getSocketAddress();

        config.setHost("second.example.com");
        Object second = config.getSocketAddress();

        assertNotSame(first, second);
        assertEquals("second.example.com", config.getHost());
    }
}
