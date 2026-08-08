package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Singleton
public class ProcessInfoLookupService {

    static final int QUEUE_CAPACITY = 1024;
    static final long LOOKUP_TIMEOUT_SECONDS = 10L;
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final ProcessInfoResolver resolver;
    private final ExecutorService executor;
    private final ScheduledExecutorService timeoutExecutor;
    private final long lookupTimeoutMillis;
    private final AtomicBoolean linkageFailureLogged = new AtomicBoolean();
    private final AtomicBoolean warmUpStarted = new AtomicBoolean();
    private final AtomicLong lastTimeoutWarning = new AtomicLong();
    private final AtomicLong lastQueueWarning = new AtomicLong();

    @Inject
    public ProcessInfoLookupService(ProcessInfoResolver resolver) {
        this(resolver, new ThreadPoolExecutor(8, 8, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "process-info-lookup");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()), createTimeoutExecutor(),
                TimeUnit.SECONDS.toMillis(LOOKUP_TIMEOUT_SECONDS));
    }

    ProcessInfoLookupService(ProcessInfoResolver resolver, ExecutorService executor) {
        this(resolver, executor, createTimeoutExecutor(),
                TimeUnit.SECONDS.toMillis(LOOKUP_TIMEOUT_SECONDS));
    }

    ProcessInfoLookupService(ProcessInfoResolver resolver, ExecutorService executor,
                             ScheduledExecutorService timeoutExecutor, long lookupTimeoutMillis) {
        this.resolver = resolver;
        this.executor = executor;
        this.timeoutExecutor = timeoutExecutor;
        this.lookupTimeoutMillis = lookupTimeoutMillis;
    }

    public CompletableFuture<ProcessInfo> lookup(InetSocketAddress clientAddress,
                                                 InetSocketAddress proxyAddress) {
        CompletableFuture<ProcessInfo> result = new CompletableFuture<>();
        AtomicReference<Future<?>> taskReference = new AtomicReference<>();
        try {
            Future<?> task = executor.submit(() -> {
                try {
                    ProcessInfo processInfo = resolver.resolve(clientAddress, proxyAddress);
                    log.debug("ProcessInfoLookupService.lookup: {}", processInfo);
                    result.complete(processInfo == null ? ProcessInfo.unknown() : processInfo);
                } catch (Exception exception) {
                    log.warn("Unexpected process lookup failure", exception);
                    result.complete(ProcessInfo.withStatus(ProcessInfo.LookupStatus.ERROR));
                } catch (LinkageError error) {
                    if (linkageFailureLogged.compareAndSet(false, true)) {
                        log.warn("Process lookup dependency is unavailable: {}", error.toString());
                    }
                    result.complete(ProcessInfo.withStatus(ProcessInfo.LookupStatus.UNSUPPORTED));
                }
            });
            taskReference.set(task);
        } catch (RejectedExecutionException exception) {
            if (shouldWarn(lastQueueWarning)) {
                log.warn("Process lookup queue is full ({})", executorState());
            } else {
                log.debug("Process lookup queue is full ({})", executorState());
            }
            result.complete(errorResult());
            return result;
        }

        ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
            if (result.complete(errorResult())) {
                Future<?> task = taskReference.get();
                if (task != null) {
                    task.cancel(true);
                }
                if (executor instanceof ThreadPoolExecutor threadPool) {
                    threadPool.purge();
                }
                if (shouldWarn(lastTimeoutWarning)) {
                    log.warn("Process lookup timed out after {} ms for {} -> {} ({})",
                            lookupTimeoutMillis, clientAddress, proxyAddress, executorState());
                } else {
                    log.debug("Process lookup timed out after {} ms for {} -> {} ({})",
                            lookupTimeoutMillis, clientAddress, proxyAddress, executorState());
                }
            }
        }, lookupTimeoutMillis, TimeUnit.MILLISECONDS);
        result.whenComplete((ignored, throwable) -> timeout.cancel(false));
        return result;
    }

    @PostConstruct
    void warmUp() {
        if (!warmUpStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                long startedAt = System.nanoTime();
                try {
                    resolver.warmUp();
                    log.info("Process lookup warm-up completed in {} ms",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                } catch (Exception | LinkageError exception) {
                    log.warn("Process lookup warm-up failed after {} ms: {}",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), exception.toString());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("Process lookup warm-up was rejected ({})", executorState());
        }
    }

    private String executorState() {
        if (executor instanceof ThreadPoolExecutor threadPool) {
            return "active=" + threadPool.getActiveCount() + ", queue=" + threadPool.getQueue().size();
        }
        return "executor=" + executor.getClass().getSimpleName();
    }

    private static ProcessInfo errorResult() {
        return ProcessInfo.withStatus(ProcessInfo.LookupStatus.ERROR);
    }

    private static boolean shouldWarn(AtomicLong lastWarning) {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        return now - previous >= WARN_INTERVAL_NANOS && lastWarning.compareAndSet(previous, now);
    }

    private static ScheduledExecutorService createTimeoutExecutor() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "process-info-timeout");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        timeoutExecutor.shutdownNow();
    }
}
