package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessInfoLookupServiceUnitTest {

    @Test
    public void runsLookupOutsideTheCallerThread() throws Exception {
        AtomicReference<String> lookupThread = new AtomicReference<>();
        ProcessInfoResolver resolver = (client, proxy) -> {
            lookupThread.set(Thread.currentThread().getName());
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.FOUND);
        };
        ThreadPoolExecutor executor = executor(2);
        ProcessInfoLookupService service = new ProcessInfoLookupService(resolver, executor);

        try {
            ProcessInfo result = service.lookup(address(51000), address(9090)).get(1, TimeUnit.SECONDS);

            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, result.getLookupStatus());
            Assert.assertEquals("process-info-test", lookupThread.get());
            Assert.assertNotEquals(Thread.currentThread().getName(), lookupThread.get());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void returnsErrorImmediatelyWhenTheBoundedQueueIsFull() throws Exception {
        CountDownLatch firstLookupStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLookup = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ProcessInfoResolver resolver = (client, proxy) -> {
            if (calls.incrementAndGet() == 1) {
                firstLookupStarted.countDown();
                try {
                    releaseFirstLookup.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.FOUND);
        };
        ThreadPoolExecutor executor = executor(1);
        ProcessInfoLookupService service = new ProcessInfoLookupService(resolver, executor);

        try {
            CompletableFuture<ProcessInfo> running = service.lookup(address(51001), address(9090));
            Assert.assertTrue(firstLookupStarted.await(1, TimeUnit.SECONDS));
            CompletableFuture<ProcessInfo> queued = service.lookup(address(51002), address(9090));

            ProcessInfo rejected = service.lookup(address(51003), address(9090)).get(100, TimeUnit.MILLISECONDS);

            Assert.assertEquals(ProcessInfo.LookupStatus.ERROR, rejected.getLookupStatus());
            releaseFirstLookup.countDown();
            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, running.get(1, TimeUnit.SECONDS).getLookupStatus());
            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, queued.get(1, TimeUnit.SECONDS).getLookupStatus());
        } finally {
            releaseFirstLookup.countDown();
            service.shutdown();
        }
    }

    @Test
    public void returnsErrorAndInterruptsLookupAfterTheHardTimeout() throws Exception {
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ProcessInfoResolver resolver = (client, proxy) -> {
            lookupStarted.countDown();
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.FOUND);
        };
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        ProcessInfoLookupService service = new ProcessInfoLookupService(
                resolver, executor(1), timeoutExecutor, 40L);

        try {
            CompletableFuture<ProcessInfo> future = service.lookup(address(51006), address(9090));
            Assert.assertTrue(lookupStarted.await(1, TimeUnit.SECONDS));

            Assert.assertEquals(ProcessInfo.LookupStatus.ERROR,
                    future.get(1, TimeUnit.SECONDS).getLookupStatus());
            Assert.assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void warmsUpOnceOutsideTheCallerThread() throws Exception {
        CountDownLatch warmed = new CountDownLatch(1);
        AtomicReference<String> warmUpThread = new AtomicReference<>();
        ProcessInfoResolver resolver = new ProcessInfoResolver() {
            @Override
            public ProcessInfo resolve(InetSocketAddress clientAddress, InetSocketAddress proxyAddress) {
                return ProcessInfo.withStatus(ProcessInfo.LookupStatus.FOUND);
            }

            @Override
            public void warmUp() {
                warmUpThread.set(Thread.currentThread().getName());
                warmed.countDown();
            }
        };
        ProcessInfoLookupService service = new ProcessInfoLookupService(resolver, executor(1));

        try {
            service.warmUp();
            service.warmUp();

            Assert.assertTrue(warmed.await(1, TimeUnit.SECONDS));
            Assert.assertEquals("process-info-test", warmUpThread.get());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void keepsTheWorkerAliveWhenAResolverDependencyIsMissing() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProcessInfoResolver resolver = (client, proxy) -> {
            if (calls.incrementAndGet() == 1) {
                throw new NoClassDefFoundError("oshi/SystemInfo");
            }
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.FOUND);
        };
        ProcessInfoLookupService service = new ProcessInfoLookupService(resolver, executor(2));

        try {
            ProcessInfo unsupported = service.lookup(address(51004), address(9090)).get(1, TimeUnit.SECONDS);
            ProcessInfo recovered = service.lookup(address(51005), address(9090)).get(1, TimeUnit.SECONDS);

            Assert.assertEquals(ProcessInfo.LookupStatus.UNSUPPORTED, unsupported.getLookupStatus());
            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, recovered.getLookupStatus());
        } finally {
            service.shutdown();
        }
    }

    private static ThreadPoolExecutor executor(int queueCapacity) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> new Thread(runnable, "process-info-test"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static InetSocketAddress address(int port) {
        return new InetSocketAddress("127.0.0.1", port);
    }
}
