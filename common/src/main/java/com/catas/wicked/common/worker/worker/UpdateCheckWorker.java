package com.catas.wicked.common.worker.worker;

import com.catas.wicked.common.worker.AbstractScheduledWorker;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class UpdateCheckWorker extends AbstractScheduledWorker {

    @Override
    protected boolean doWork(boolean manually) {
        log.info("Checking update...");
        return true;
    }

    @Override
    public long getDelay() {
        return 5 * 60 * 1000;
    }
}
