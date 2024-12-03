package com.catas.wicked.common.provider;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.SystemProxyConfig;
import com.catas.wicked.common.util.SystemUtils;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Singleton
@Requires(os = Requires.Family.MAC_OS)
public class MacSysProxyProvider implements SysProxyProvider {

    private static final String NETWORK_SETUP = "networksetup";
    private static final String BYPASS_DOMAIN = "proxybypassdomains";
    private static final String PROXY_TYPE_WEB = "webproxy";
    private static final String PROXY_TYPE_SECURE_WEB = "securewebproxy";
    private static final String PROXY_TYPE_SOCKS = "socksfirewallproxy";
    private static final List<String> PROXY_TYPES = List.of(PROXY_TYPE_WEB, PROXY_TYPE_SECURE_WEB);

    @Inject
    private ApplicationConfig appConfig;

    @Override
    public List<SystemProxyConfig> getSysProxyConfig() {
        List<String> networkServices = getNetworkServices();
        if (networkServices.isEmpty()) {
            log.error("No available network services");
            return Collections.emptyList();
        }

        List<SystemProxyConfig> result = new ArrayList<>();
        try {
            for (String networkService: networkServices) {
                for (String proxyType : PROXY_TYPES) {
                    String httpProxySettings = getProxySettings(networkService, proxyType);
                    SystemProxyConfig httpProxyConfig = parseFromNetworkSetup(httpProxySettings);
                    httpProxyConfig.setProxyType(proxyType);
                    httpProxyConfig.setNetworkService(networkService);
                    result.add(httpProxyConfig);
                }
            }
        } catch (Exception e) {
            log.error("Error in getting MacOs system proxy settings.", e);
        }
        return result;
    }

    private String getProxySettings(String networkService, String proxyType) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                NETWORK_SETUP,
                "-get" + proxyType,
                networkService);
        return SystemUtils.runCommand(builder);
    }

    @Override
    public void setSysProxyConfig() {
        boolean enabled = appConfig.getSettings().isSystemProxy();
        setSysProxy(enabled);
    }

    @Override
    public void clearSysProxy() {
        setSysProxy(false);
    }

    private void setSysProxy(boolean enabled) {
        List<String> networkServices = getNetworkServices();
        if (networkServices.isEmpty()) {
            log.error("No available network services");
            return;
        }

        String host = appConfig.getHost();
        int port = appConfig.getSettings().getPort();
        try {
            for (String networkService : networkServices) {
                for (String proxyType : PROXY_TYPES) {
                    if (enabled) {
                        setProxy(networkService, proxyType, host, port);
                    }
                    setProxyState(networkService, proxyType, enabled);
                }
            }
        } catch (Exception e) {
            log.error("Error in setting MacOS system proxy.", e);
        }
    }

    @Override
    public List<String> getBypassDomains() {
        List<String> networkServices = getNetworkServices();
        if (networkServices.isEmpty()) {
            log.error("No available network services");
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();
        for (String networkService : networkServices) {
            ProcessBuilder builder = new ProcessBuilder(NETWORK_SETUP,
                    "-get" + BYPASS_DOMAIN,
                    networkService);
            try {
                String res = SystemUtils.runCommand(builder);
                List<String> domainList = Arrays.stream(res.split("\n"))
                        .filter(Objects::nonNull)
                        .filter(item -> !item.contains(" "))
                        .map(String::trim).toList();
                list.addAll(domainList);
            } catch (Exception e) {
                log.error("Error in getting MacOS bypass domains.", e);
            }
        }
        return list.stream().distinct().toList();
    }

    @Override
    public void setBypassDomains(List<String> domains) {
        if (domains == null) {
            throw new IllegalArgumentException("Domains cannot be null");
        }
        List<String> networkServices = getNetworkServices();
        if (networkServices.isEmpty()) {
            log.error("No available network services");
            return;
        }

        if (domains.isEmpty()) {
            // Specify "Empty" for <domain1> to clear all Domain Name entries.
            domains = List.of("Empty");
        } else {
            domains = domains.stream().distinct().toList();
        }

        for (String networkService: networkServices) {
            List<String> cmd = new ArrayList<>();
            cmd.add(NETWORK_SETUP);
            cmd.add("-set" + BYPASS_DOMAIN);
            cmd.add(networkService);
            cmd.addAll(domains);
            ProcessBuilder builder = new ProcessBuilder(cmd);
            try {
                SystemUtils.runCommand(builder);
            } catch (Exception e) {
                log.error("Error in setting macOs bypass domains.", e);
            }
        }
    }

    private List<String> getNetworkServices() {
        ProcessBuilder builder = new ProcessBuilder(NETWORK_SETUP, "-listallnetworkservices");
        String res = null;
        try {
            res = SystemUtils.runCommand(builder);
        } catch (Exception e) {
            log.error("Error in getting available MacOS network services.", e);
        }
        if (StringUtils.isEmpty(res)) {
            return Collections.emptyList();
        }

        // parse result
        List<String> list = new ArrayList<>();
        for (String line : res.split("\n")) {
            if (line != null && !line.contains("*")) {
                list.add(line.trim());
            }
        }
        log.info("All available network services: {}", list);
        return list;
    }

    private void setProxy(String networkService, String proxyType, String proxyAddress, int proxyPort) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                NETWORK_SETUP,
                "-set" + proxyType,
                networkService,
                proxyAddress,
                String.valueOf(proxyPort)
        );
        String res = SystemUtils.runCommand(builder);
        log.info("Setting MacOs proxy for {}, type: {}, host:{}-{}, res: {}", networkService, proxyType, proxyAddress, proxyPort, res);
    }

    private void setProxyState(String networkService, String proxyType, boolean state) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                NETWORK_SETUP,
                "-set" + proxyType + "state",
                networkService,
                state ? "on" : "off"
        );
        String res = SystemUtils.runCommand(builder);
        log.info("Setting MacOs proxy for {}, type: {}, state: {}, res: {}", networkService, proxyType, state, res);
    }

    /**
     * parse result from networksetup
     * @param lines
     * Enabled: Yes
     * Server: 127.0.0.1
     * Port: 7890
     * Authenticated Proxy Enabled: 0
     */
    public SystemProxyConfig parseFromNetworkSetup(String lines) {
        if (lines == null) {
            return null;
        }
        SystemProxyConfig config = new SystemProxyConfig();
        for (String line : lines.split("\n")) {
            String[] parts = line.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            // Trim the key and value and add them to the map
            String key = parts[0].trim();
            String value = parts[1].trim();
            switch (key.toLowerCase()) {
                case "enabled" -> config.setEnabled(StringUtils.equalsIgnoreCase(value, "Yes"));
                case "server" -> config.setServer(value);
                case "port" -> config.setPort(Integer.parseInt(value));
                case "authenticated proxy enabled" -> config.setAuthEnabled(value.equals("1"));
            }
        }
        return config;
    }
}
