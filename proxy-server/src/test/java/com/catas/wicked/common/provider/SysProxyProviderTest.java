package com.catas.wicked.common.provider;


import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.config.SystemProxyConfig;
import com.catas.wicked.BaseTest;
import com.catas.wicked.ConditionalTest;
import io.micronaut.context.annotation.Requires;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * junit5
 */
public class SysProxyProviderTest extends BaseTest {

    @Test
    @ConditionalTest(os = Requires.Family.MAC_OS)
    public void testMacGetSysProxy() throws NoSuchFieldException, IllegalAccessException {
        MacSysProxyProvider proxyProvider = new MacSysProxyProvider();
        setPrivateField(proxyProvider, "appConfig", new ApplicationConfig());
        List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
        System.out.println(configList.size());
        System.out.println(configList);
    }

    @Test
    @ConditionalTest(os = Requires.Family.MAC_OS)
    public void testMacSetSysProxy() throws Exception {
        int port = 19624;
        MacSysProxyProvider proxyProvider = new MacSysProxyProvider();
        ApplicationConfig appConfig = new ApplicationConfig();
        Settings settings = new Settings();
        settings.setSystemProxy(true);
        settings.setPort(port);
        appConfig.setSettings(settings);
        setPrivateField(proxyProvider, "appConfig", appConfig);

        // set system proxy
        proxyProvider.setSysProxyConfig();
        {
            List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
            Assertions.assertFalse(configList.isEmpty());
            configList.forEach(config -> {
                Assertions.assertTrue(config.isEnabled());
                Assertions.assertEquals(port, config.getPort());
                Assertions.assertEquals(appConfig.getHost(), config.getServer());
            });
        }

        // cancel system proxy
        settings.setSystemProxy(false);
        proxyProvider.setSysProxyConfig();
        {
            List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
            Assertions.assertFalse(configList.isEmpty());
            configList.forEach(config -> {
                Assertions.assertFalse(config.isEnabled());
            });
        }
    }

    @Test
    @ConditionalTest(os = Requires.Family.MAC_OS)
    public void testMacBypassDomains() {
        MacSysProxyProvider proxyProvider = new MacSysProxyProvider();
        final String testDomain = "catas.org";

        List<String> originDomains = proxyProvider.getBypassDomains();

        // add domain
        {
            List<String> targets = new ArrayList<>(originDomains);
            targets.add(testDomain);
            proxyProvider.setBypassDomains(targets);
            List<String> finalDomains = proxyProvider.getBypassDomains();
            Assertions.assertTrue(finalDomains.contains(testDomain));
        }

        // clear
        {
            proxyProvider.setBypassDomains(Collections.emptyList());
            List<String> finalDomains = proxyProvider.getBypassDomains();
            Assertions.assertTrue(finalDomains.isEmpty());
        }

        // reset
        {
            proxyProvider.setBypassDomains(originDomains);
            List<String> finalDomains = proxyProvider.getBypassDomains();
            Assertions.assertEquals(originDomains, finalDomains);
        }
    }

    @Test
    @ConditionalTest(os = Requires.Family.WINDOWS)
    public void testWinGetSysProxy() throws NoSuchFieldException, IllegalAccessException {
        WinSysProxyProvider provider = new WinSysProxyProvider();
        setPrivateField(provider, "appConfig", new ApplicationConfig());
        List<SystemProxyConfig> configList = provider.getSysProxyConfig();
        System.out.println(configList);
        Assertions.assertFalse(configList.isEmpty());
        Assertions.assertNotNull(configList.get(0));
    }

    @Test
    @ConditionalTest(os = Requires.Family.WINDOWS)
    public void testWinByPassDomains() throws Exception {
        WinSysProxyProvider provider = new WinSysProxyProvider();
        setPrivateField(provider, "appConfig", new ApplicationConfig());

        final String testDomain = "*.catas.org";
        List<String> target = List.of(testDomain, "*.google.com", "bing.com");

        // add domain
        {
            provider.setBypassDomains(target);
            List<String> finalDomains = provider.getBypassDomains();
            Assertions.assertTrue(finalDomains.contains(testDomain));
            Assertions.assertEquals(finalDomains, target);
        }

        // clear
        {
            provider.setBypassDomains(Collections.emptyList());
            List<String> finalDomains = provider.getBypassDomains();
            Assertions.assertTrue(finalDomains.isEmpty());
        }
    }

    @Test
    @ConditionalTest(os = Requires.Family.WINDOWS)
    public void testWinSetSysProxy() throws NoSuchFieldException, IllegalAccessException {
        int port = 19624;
        WinSysProxyProvider proxyProvider = new WinSysProxyProvider();
        ApplicationConfig appConfig = new ApplicationConfig();
        Settings settings = new Settings();
        settings.setSystemProxy(true);
        settings.setPort(port);
        appConfig.setSettings(settings);
        setPrivateField(proxyProvider, "appConfig", appConfig);

        // set system proxy
        proxyProvider.setSysProxyConfig();
        {
            List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
            Assertions.assertFalse(configList.isEmpty());
            SystemProxyConfig config = configList.get(0);
            Assertions.assertTrue(config.isEnabled());
            Assertions.assertEquals(port, config.getPort());
            Assertions.assertEquals(appConfig.getHost(), config.getServer());
        }

        // cancel system proxy
        settings.setSystemProxy(false);
        proxyProvider.setSysProxyConfig();
        {
            List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
            Assertions.assertFalse(configList.isEmpty());
            Assertions.assertFalse(configList.get(0).isEnabled());
        }
    }

    @Test
    public void testParseSysProxyConfig() throws NoSuchFieldException, IllegalAccessException {
        MacSysProxyProvider proxyProvider = new MacSysProxyProvider();
        setPrivateField(proxyProvider, "appConfig", new ApplicationConfig());
        {
            String lines = """
                    Enabled: Yes
                    Server: 127.0.0.1
                    Port: 7890
                    Authenticated Proxy Enabled: 0""";
            SystemProxyConfig config = proxyProvider.parseFromNetworkSetup(lines);
            Assertions.assertNotNull(config);
            Assertions.assertTrue(config.isEnabled());
            Assertions.assertEquals("127.0.0.1", config.getServer());
            Assertions.assertEquals(7890, config.getPort());
        }

        {
            String lines = """
                    Enabled: No
                    Server:\s
                    Port: 0
                    Authenticated Proxy Enabled: 0""";
            SystemProxyConfig config = proxyProvider.parseFromNetworkSetup(lines);
            Assertions.assertNotNull(config);
            Assertions.assertFalse(config.isEnabled());
            Assertions.assertTrue(config.getServer().isBlank());
            Assertions.assertEquals(0, config.getPort());
        }
    }
}