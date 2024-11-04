package com.catas.wicked.common.worker;

public interface ScheduledWorker extends Runnable {

    /**
     * start scheduled task
     */
    void start();

    /**
     * pause task
     */
    void pause();

    /**
     * execute task manually
     */
    boolean invoke();

    /**
     * time delay for auto execution
     */
    long getDelay();

    default long getInitDelay() {
        return 0;
    }
}
