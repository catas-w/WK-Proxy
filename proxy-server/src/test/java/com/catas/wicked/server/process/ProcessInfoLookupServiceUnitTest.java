package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
    public void returnsUnknownImmediatelyWhenTheBoundedQueueIsFull() throws Exception {
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

            Assert.assertEquals(ProcessInfo.LookupStatus.UNKNOWN, rejected.getLookupStatus());
            releaseFirstLookup.countDown();
            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, running.get(1, TimeUnit.SECONDS).getLookupStatus());
            Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, queued.get(1, TimeUnit.SECONDS).getLookupStatus());
        } finally {
            releaseFirstLookup.countDown();
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
