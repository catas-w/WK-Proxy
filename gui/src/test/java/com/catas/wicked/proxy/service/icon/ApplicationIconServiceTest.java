package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import org.junit.After;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ApplicationIconServiceTest {

    private ApplicationIconService service;

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    public void nativeIconTakesPrecedenceAndIsCached() {
        ApplicationIconData nativeImage = new ApplicationIconData.Png(new byte[]{1});
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger bundledCalls = new AtomicInteger();
        service = service(info -> {
            nativeCalls.incrementAndGet();
            return Optional.of(nativeImage);
        }, info -> {
            bundledCalls.incrementAndGet();
            return Optional.of(new ApplicationIconData.Png(new byte[]{2}));
        });

        assertSame(nativeImage, service.loadData(found("/Applications/Browser.app/Contents/MacOS/Browser"))
                .join().orElseThrow());
        assertSame(nativeImage, service.loadData(found("/Applications/Browser.app/Contents/MacOS/Browser"))
                .join().orElseThrow());
        assertEquals(1, nativeCalls.get());
        assertEquals(0, bundledCalls.get());
    }

    @Test
    public void bundledIconIsUsedAfterNativeMiss() {
        ApplicationIconData bundledImage = new ApplicationIconData.Png(new byte[]{1});
        service = service(info -> Optional.empty(), info -> Optional.of(bundledImage));

        assertSame(bundledImage, service.loadData(found("/usr/bin/curl")).join().orElseThrow());
    }

    @Test
    public void concurrentRequestsShareOneLookup() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service = service(info -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.of(new ApplicationIconData.Png(new byte[]{1}));
        }, info -> Optional.empty());

        var first = service.loadData(found("/Applications/Browser.app/Contents/MacOS/Browser"));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var second = service.loadData(found("/Applications/Browser.app/Contents/MacOS/Browser"));
        assertSame(first, second);
        release.countDown();
        assertTrue(first.get(2, TimeUnit.SECONDS).isPresent());
        assertEquals(1, calls.get());
    }

    @Test
    public void rejectedLookupReturnsEmptyWithoutThrowing() {
        ThreadPoolExecutor executor = executor();
        executor.shutdownNow();
        service = new ApplicationIconService(info -> Optional.of(new ApplicationIconData.Png(new byte[]{1})),
                info -> Optional.empty(), executor);

        assertFalse(service.loadData(found("C:\\Apps\\Browser.exe")).join().isPresent());
    }

    @Test
    public void cacheKeyNormalizesWindowsPathsOnlyCaseInsensitively() {
        ProcessInfo info = found("C:\\Apps\\Browser.EXE");
        assertEquals("path:c:/apps/browser.exe", ApplicationIconService.cacheKey(info, "Windows 11"));
        assertEquals("path:C:/Apps/Browser.EXE", ApplicationIconService.cacheKey(info, "Mac OS X"));
    }

    @Test
    public void unresolvedProcessDoesNotScheduleLookup() {
        AtomicInteger calls = new AtomicInteger();
        service = service(info -> {
            calls.incrementAndGet();
            return Optional.empty();
        }, info -> Optional.empty());

        assertFalse(service.loadData(ProcessInfo.withStatus(ProcessInfo.LookupStatus.NOT_FOUND)).join().isPresent());
        assertEquals(0, calls.get());
    }

    @Test
    public void deterministicMissIsCached() {
        AtomicInteger calls = new AtomicInteger();
        service = service(info -> {
            calls.incrementAndGet();
            return Optional.empty();
        }, info -> Optional.empty());

        ProcessInfo process = found("/Applications/Missing.app/Contents/MacOS/Missing");
        assertFalse(service.loadData(process).join().isPresent());
        assertFalse(service.loadData(process).join().isPresent());
        assertEquals(1, calls.get());
    }

    private ApplicationIconService service(ApplicationIconProvider nativeProvider,
                                           ApplicationIconProvider bundledProvider) {
        return new ApplicationIconService(nativeProvider, bundledProvider, executor());
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "application-icon-test");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static ProcessInfo found(String path) {
        return ProcessInfo.builder()
                .applicationName("Browser")
                .applicationExecutablePath(path)
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
    }
}
