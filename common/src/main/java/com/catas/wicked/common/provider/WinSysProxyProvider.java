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
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * cmd:
 * reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer
 */
@Slf4j
@Singleton
@Requires(os = Requires.Family.WINDOWS)
public class WinSysProxyProvider implements SysProxyProvider {

    private static final String REG_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    private static final String QUOTE_REG_PATH = "\"" + REG_PATH + "\"";
    private static final String KEY_PROXY_SERVER = "ProxyServer";
    private static final String KEY_PROXY_ENABLE = "ProxyEnable";
    private static final String KEY_PROXY_OVERRIDE = "ProxyOverride";

    @Inject
    private ApplicationConfig appConfig;

    @Override
    public List<SystemProxyConfig> getSysProxyConfig() {
        try {
            SystemProxyConfig config = new SystemProxyConfig();
            String proxyServerRes = getRegistryValue(KEY_PROXY_SERVER);
            parseFromLines(KEY_PROXY_SERVER, proxyServerRes, value -> {
                String[] split = value.split(":");
                config.setServer(split[0]);
                config.setPort(Integer.parseInt(split[1]));
            });

            String proxyEnableRes = getRegistryValue(KEY_PROXY_ENABLE);
            parseFromLines(KEY_PROXY_ENABLE, proxyEnableRes, value -> {
                int enabled = Integer.valueOf(value.substring(2), 16);
                config.setEnabled(enabled == 1);
            });
            return Collections.singletonList(config);
        } catch (Exception e) {
            log.error("Error in getting Windows system proxy.", e);
        }
        return Collections.emptyList();
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
        String proxyServer = String.format("%s:%s", appConfig.getHost(), appConfig.getSettings().getPort());
        String enableProxy = enabled ? "1" : "0";

        try {
            String res = setRegistryValue(KEY_PROXY_ENABLE, enableProxy, "REG_DWORD");
            if (enabled) {
                res += setRegistryValue(KEY_PROXY_SERVER, proxyServer, "REG_SZ");
            }
            // Notify the system about the changes
            // ProcessBuilder notifyBuilder = new ProcessBuilder("RUNDLL32.EXE", "inetcpl.cpl,ProxyStub");
            // SystemUtils.runCommand(notifyBuilder);

            log.info("Set Windows system proxy with {}, enabled: {}, res: {}", proxyServer, enableProxy, res);
        } catch (Exception e) {
            log.error("Error in setting Windows system proxy.", e);
        }
    }

    @Override
    public List<String> getBypassDomains() {
        List<String> list = new ArrayList<>();
        try {
            String res = getRegistryValue(KEY_PROXY_OVERRIDE);
            parseFromLines(KEY_PROXY_OVERRIDE, res, value -> {
                if (StringUtils.isBlank(value)) {
                    return;
                }
                String[] domains = value.trim().split(";");
                list.addAll(List.of(domains));
            });
        } catch (Exception e) {
            log.error("Error in getting Windows bypass domains.", e);
        }
        return list;
    }

    @Override
    public void setBypassDomains(List<String> domains) {
        if (domains == null) {
            throw new IllegalArgumentException("Domains cannot be null");
        }
        String value = String.join(";", domains);
        try {
            setRegistryValue(KEY_PROXY_OVERRIDE, value, "REG_SZ");
        } catch (Exception e) {
            log.error("Error in setting Windows bypass domains.", e);
        }
    }

    private String setRegistryValue(String key, String value, String valueType) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "reg",
                "add",
                QUOTE_REG_PATH,
                "/v", key, "/t", valueType, "/d", value, "/f");
        return SystemUtils.runCommand(builder);
    }

    private String getRegistryValue(String valueName) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "reg",
                "query",
                QUOTE_REG_PATH,
                "/v", valueName);
        return SystemUtils.runCommand(builder);
    }

    /**
     *
     * @param lines
     * HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Internet Settings
     *     ProxyServer    REG_SZ    127.0.0.1:10809
     */
    private void parseFromLines(String key, String lines, Consumer<String> func) {
        if (lines == null) {
            return;
        }
        for (String line : lines.split("\n")) {
            if (!lines.contains(key)) {
                continue;
            }
            String[] parts = line.trim().split("\s+");
            if (parts.length < 3) {
                continue;
            }
            String value = parts[2];
            func.accept(value);
        }
    }
}
