package com.catas.wicked.common.worker;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.executor.ScheduledThreadPoolService;
import com.catas.wicked.common.provider.SysProxyProvider;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RunnableScheduledFuture;

import static com.catas.wicked.common.constant.WorkerConstant.SYS_PROXY_WORKER;

@Slf4j
@Singleton
public class ScheduledManager {

    private final ConcurrentHashMap<String, ScheduledWorker> workerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RunnableScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private SysProxyProvider sysProxyProvider;

    @PostConstruct
    public void init() {
        register(SYS_PROXY_WORKER, new SystemProxyWorker(appConfig, sysProxyProvider));
    }

    /**
     * start a scheduledWorker
     * not thread-safe
     * @param name unique name
     * @param worker ScheduledWorker
     */
    public void register(String name, ScheduledWorker worker) {
        if (StringUtils.isBlank(name) || worker == null) {
            throw new IllegalArgumentException("Worker name cannot be empty.");
        }
        if (workerMap.containsKey(name)) {
            throw new IllegalArgumentException("Worker name already exist.");
        }
        RunnableScheduledFuture<?> future = (RunnableScheduledFuture<?>) ScheduledThreadPoolService.getInstance()
                .submit(worker, worker.getDelay());
        workerMap.put(name, worker);
        futureMap.put(name, future);
        worker.start();
    }

    /**
     * cancel a scheduledWorker
     * @param name name
     */
    public void cancel(String name) {
        if (StringUtils.isBlank(name) || !workerMap.containsKey(name)) {
            log.error("Worker not exist: {}", name);
            return;
        }
        ScheduledWorker worker = workerMap.get(name);
        worker.pause();
        RunnableScheduledFuture<?> future = futureMap.get(name);
        boolean res = ScheduledThreadPoolService.getInstance().cancel(future);

        workerMap.remove(name);
        futureMap.remove(name);
        log.info("Cancelled worker: {} with success: {}.", name, res);
    }

    /**
     * invoke task once
     */
    public void invoke(String name) {
        if (StringUtils.isBlank(name) || !workerMap.containsKey(name)) {
            throw new IllegalArgumentException("Worker not exist: " + name);
        }
        ScheduledWorker worker = workerMap.get(name);
        worker.invoke();
        log.info("Manually invoked worker: {}", name);
    }
}
