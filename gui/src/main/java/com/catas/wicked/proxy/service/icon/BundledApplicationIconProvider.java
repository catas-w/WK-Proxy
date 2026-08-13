package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class BundledApplicationIconProvider implements ApplicationIconProvider {

    private static final Map<String, String> ICONS = icons();

    @Override
    public Optional<ApplicationIconData> load(ProcessInfo info) {
        String candidate = String.join(" ",
                StringUtils.defaultString(info.getApplicationName()),
                StringUtils.defaultString(info.getOwnerProcessName()),
                StringUtils.defaultString(info.getApplicationExecutablePath()),
                StringUtils.defaultString(info.getOwnerExecutablePath())).toLowerCase(Locale.ROOT);
        return ICONS.entrySet().stream()
                .filter(entry -> candidate.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(this::loadResource)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<ApplicationIconData> loadResource(String resource) {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new ApplicationIconData.Png(stream.readAllBytes()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, String> icons() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("wizard proxy", "/image/wk-proxy.2.png");
        result.put("visual studio code", "/image/application/vscode.png");
        result.put("code.exe", "/image/application/vscode.png");
        result.put("intellij idea", "/image/application/intellij-idea.png");
        result.put("idea64.exe", "/image/application/intellij-idea.png");
        result.put("google chrome", "/image/application/chrome.png");
        result.put("chrome.exe", "/image/application/chrome.png");
        result.put("microsoft edge", "/image/application/edge.png");
        result.put("msedge.exe", "/image/application/edge.png");
        result.put("firefox", "/image/application/firefox.png");
        result.put("safari", "/image/application/safari.png");
        result.put("postman", "/image/application/postman.png");
        result.put("curl", "/image/application/curl.png");
        return result;
    }
}
