package com.catas.wicked.common.worker.worker;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.provider.VersionCheckProvider;
import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.common.worker.AbstractScheduledWorker;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class UpdateCheckWorker extends AbstractScheduledWorker {

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private VersionCheckProvider versionCheckProvider;

    @Override
    protected boolean doWork(boolean manually) {
        try {
            String version = versionCheckProvider.fetchLatestVersion().getLeft();
            if (CommonUtils.compareVersions(appConfig.getAppVersion(), version) < 0) {
                appConfig.getObservableConfig().setHasNewVersion(true);
            }
        } catch (Exception e) {
            log.error("Error in fetching latest version info.", e);
            return false;
        }
        return true;
    }

    @Override
    public long getDelay() {
        return 60 * 60 * 1000;
    }

    @Override
    public long getInitDelay() {
        return 10 * 1000;
    }
}
