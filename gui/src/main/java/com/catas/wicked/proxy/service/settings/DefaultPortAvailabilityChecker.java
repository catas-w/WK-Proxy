package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.util.WebUtils;
import jakarta.inject.Singleton;

@Singleton
public class DefaultPortAvailabilityChecker implements PortAvailabilityChecker {

    @Override
    public boolean isAvailable(int port) {
        return WebUtils.isPortAvailable(port);
    }
}
