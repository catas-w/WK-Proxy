package com.catas.wicked.proxy.service.icon;

import java.util.Locale;

final class NativeApplicationIconProvider {

    private NativeApplicationIconProvider() {
    }

    static ApplicationIconProvider create() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return new MacApplicationIconProvider();
        }
        if (osName.contains("win")) {
            return new WindowsApplicationIconProvider();
        }
        return processInfo -> java.util.Optional.empty();
    }
}
