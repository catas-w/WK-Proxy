package com.catas.wicked.common.worker.worker;

import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.common.worker.AbstractScheduledWorker;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CertCheckWorker extends AbstractScheduledWorker {

    @Inject
    private CertManager certManager;

    @Override
    protected boolean doWork(boolean manually) {
        try {
            certManager.checkSelectedCertInstalled();
        } catch (Exception e) {
            log.error("Error in CertCheckWorker");
        }
        return true;
    }

    @Override
    public long getDelay() {
        return 60 * 1000;
    }

    @Override
    public long getInitDelay() {
        return 5 * 1000;
    }
}
