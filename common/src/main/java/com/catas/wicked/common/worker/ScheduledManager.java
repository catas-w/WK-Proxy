package com.catas.wicked.common.worker;

import com.catas.wicked.common.executor.ScheduledThreadPoolService;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.worker.worker.CertCheckWorker;
import com.catas.wicked.common.worker.worker.FilePurgeWorker;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.catas.wicked.common.constant.WorkerConstant.CHECK_CERT_WORKER;
import static com.catas.wicked.common.constant.WorkerConstant.CHECK_UPDATE_WORKER;
import static com.catas.wicked.common.constant.WorkerConstant.SYS_PROXY_WORKER;

@Slf4j
@Singleton
public class ScheduledManager {

    private final ConcurrentHashMap<String, ScheduledWorker> workerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RunnableScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    @Inject
    @Named("systemProxyWorker")
    private ScheduledWorker systemProxyWorker;

    @Inject
    @Named("updateCheckWorker")
    private ScheduledWorker updateCheckWorker;

    @Inject
    @Named("certCheckWorker")
    private CertCheckWorker certCheckWorker;

    @Inject
    @Named("filePurgeWorker")
    private FilePurgeWorker filePurgeWorker;

    @PostConstruct
    public void init() {
        // register default workers
        register(SYS_PROXY_WORKER, systemProxyWorker);
        register(CHECK_UPDATE_WORKER, updateCheckWorker);
        register(CHECK_CERT_WORKER, certCheckWorker);
        register("filePurgeWorker", filePurgeWorker);
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
                .submit(worker, worker.getInitDelay(), worker.getDelay(), TimeUnit.MILLISECONDS);
        workerMap.put(name, worker);
        futureMap.put(name, future);
        worker.start();
    }

    /**
     * cancel a scheduledWorker
     * @param name name
     */
    public void cancel(String name) {
        try {
            checkWorkerExist(name);
        } catch (IllegalArgumentException ignored) {
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

    public void pause(String name) {
        try {
            checkWorkerExist(name);
        } catch (IllegalArgumentException ignored) {
            log.error("Worker not exist: {}", name);
            return;
        }
        ScheduledWorker worker = workerMap.get(name);
        worker.pause();
    }

    public void resume(String name) {
        try {
            checkWorkerExist(name);
        } catch (IllegalArgumentException ignored) {
            log.error("Worker not exist: {}", name);
            return;
        }
        ScheduledWorker worker = workerMap.get(name);
        worker.start();
    }

    /**
     * invoke task once
     */
    public void invoke(String name) {
        checkWorkerExist(name);
        ScheduledWorker worker = workerMap.get(name);
        boolean res = worker.invoke();
        log.info("Manually invoked worker: {}, success: {}", name, res);
    }

    public void invokeAsync(String name) {
        ThreadPoolService.getInstance().run(() -> invoke(name));
    }

    private void checkWorkerExist(String name) {
        if (StringUtils.isBlank(name) || !workerMap.containsKey(name)) {
            throw new IllegalArgumentException("Worker not exist: " + name);
        }
    }
}
