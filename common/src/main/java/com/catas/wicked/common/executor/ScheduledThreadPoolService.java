package com.catas.wicked.common.executor;

import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ScheduledThreadPoolService {

    private static final int CORE_SIZE = 20;
    private static final String PREFIX = "common-scheduled-thread-";

    private final ScheduledThreadPoolExecutor service;

    private ScheduledThreadPoolService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int cnt = 0;
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, PREFIX + cnt ++);
            }
        };
        this.service = new ScheduledThreadPoolExecutor(CORE_SIZE, threadFactory);
    }

    public ScheduledFuture<?> submit(Runnable task, long initialDelay, long delay, TimeUnit timeUnit) {
        return service.scheduleWithFixedDelay(task, initialDelay, delay, timeUnit);
    }

    public ScheduledFuture<?> submit(Runnable task, long delay) {
        return service.scheduleAtFixedRate(task, 0, delay, TimeUnit.MILLISECONDS);
    }

    public boolean cancel(RunnableScheduledFuture<?> task) {
        if (task == null) {
            return false;
        }
        return service.remove(task);
    }

    public void shutdown() {
        service.shutdownNow();
    }

    public static boolean owns(String threadName) {
        return threadName != null && threadName.startsWith(PREFIX);
    }

    public static ScheduledThreadPoolService getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final ScheduledThreadPoolService instance = new ScheduledThreadPoolService();
    }
}
