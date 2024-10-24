package com.catas.wicked.common.executor;

import java.util.concurrent.*;

public class ThreadPoolService {

    private static final int CORE_POOL_SIZE = 16;

    private static final int MAX_POOL_SIZE = 64;

    private static final long ALIVE_TIME = 0L;

    private final ExecutorService service;

    private static final String PREFIX = "common-thread-pool-";

    private ThreadPoolService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int cnt = 0;
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, PREFIX + cnt ++);
            }
        };

        service = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(1024), threadFactory
        );
    }

    public void run(Runnable task) {
        service.execute(task);
    }

    public Future<?> submit(Runnable task) {
        return service.submit(task);
    }

    public void shutdown() {
        service.shutdownNow();
    }

    public static ThreadPoolService getInstance() {
        return Holder.instance;
    }

    public static boolean owns(String threadName) {
        return threadName != null && threadName.startsWith(PREFIX);
    }

    public static class Holder {
        private static final ThreadPoolService instance = new ThreadPoolService();
    }
}
