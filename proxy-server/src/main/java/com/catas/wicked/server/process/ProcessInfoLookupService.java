package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ProcessInfoLookupService {

    static final int QUEUE_CAPACITY = 64;
    static final long LOOKUP_TIMEOUT_SECONDS = 1L;

    private final ProcessInfoResolver resolver;
    private final ExecutorService executor;

    @Inject
    public ProcessInfoLookupService(ProcessInfoResolver resolver) {
        this(resolver, new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "process-info-lookup");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()));
    }

    ProcessInfoLookupService(ProcessInfoResolver resolver, ExecutorService executor) {
        this.resolver = resolver;
        this.executor = executor;
    }

    public CompletableFuture<ProcessInfo> lookup(InetSocketAddress clientAddress,
                                                 InetSocketAddress proxyAddress) {
        CompletableFuture<ProcessInfo> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    ProcessInfo processInfo = resolver.resolve(clientAddress, proxyAddress);
                    // System.out.println("ProcessInfoLookupService.lookup: " + processInfo);
                    result.complete(processInfo == null ? ProcessInfo.unknown() : processInfo);
                } catch (Exception exception) {
                    log.warn("Unexpected process lookup failure", exception);
                    result.complete(ProcessInfo.withStatus(ProcessInfo.LookupStatus.ERROR));
                }
            });
        } catch (RejectedExecutionException exception) {
            log.debug("Process lookup queue is full");
            result.complete(ProcessInfo.unknown());
        }
        return result.completeOnTimeout(ProcessInfo.unknown(), LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
