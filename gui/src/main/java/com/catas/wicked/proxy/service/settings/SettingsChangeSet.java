package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.Settings;

import java.util.Objects;

public record SettingsChangeSet(
        boolean languageChanged,
        boolean generalChanged,
        boolean uiChanged,
        boolean portChanged,
        boolean proxyChanged,
        boolean sslChanged,
        boolean certificateChanged,
        boolean externalProxyChanged
) {

    public static SettingsChangeSet between(Settings before, Settings after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        return new SettingsChangeSet(
                before.getLanguage() != after.getLanguage(),
                !Objects.equals(before.getMaxContentSize(), after.getMaxContentSize())
                        || !Objects.equals(before.getRecordExcludeList(), after.getRecordExcludeList()),
                before.isShowButtonLabel() != after.isShowButtonLabel()
                        || before.isShowApplicationRequestCount() != after.isShowApplicationRequestCount(),
                !Objects.equals(before.getPort(), after.getPort()),
                before.isThrottle() != after.isThrottle()
                        || before.getThrottlePreset() != after.getThrottlePreset()
                        || before.isEnableSysProxyOnLaunch() != after.isEnableSysProxyOnLaunch()
                        || !Objects.equals(before.getSysProxyBypassList(), after.getSysProxyBypassList()),
                before.isHandleSsl() != after.isHandleSsl()
                        || !Objects.equals(before.getSslExcludeList(), after.getSslExcludeList()),
                !Objects.equals(before.getSelectedCert(), after.getSelectedCert()),
                before.isEnableExProxy() != after.isEnableExProxy()
                        || !Objects.equals(before.getExternalProxy(), after.getExternalProxy())
        );
    }

    public boolean hasChanges() {
        return languageChanged || generalChanged || uiChanged || portChanged || proxyChanged || sslChanged
                || certificateChanged || externalProxyChanged;
    }
}
