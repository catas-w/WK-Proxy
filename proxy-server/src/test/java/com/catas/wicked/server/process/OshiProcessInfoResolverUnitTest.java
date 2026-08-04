package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class OshiProcessInfoResolverUnitTest {

    @Test
    public void resolvesWindowsOwnerAndHighestParentWithTheSameExecutable() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("127.0.0.1", 51000, "127.0.0.1", 9090, 101)));
        query.processes.put(101, process(101, 100, "chrome.exe", "C:\\Chrome\\chrome.exe"));
        query.processes.put(100, process(100, 1, "chrome.exe", "C:\\Chrome\\chrome.exe"));
        OshiProcessInfoResolver resolver = resolver(query, "Windows 11");

        ProcessInfo result = resolver.resolve(address("127.0.0.1", 51000), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, result.getLookupStatus());
        Assert.assertEquals(101L, result.getOwnerPid());
        Assert.assertEquals(100L, result.getApplicationPid());
        Assert.assertEquals("chrome.exe", result.getApplicationName());
    }

    @Test
    public void resolvesMacBundleAndTreatsIpv4AndIpv6LoopbackAsEquivalent() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("::1", 52000, "::1", 9090, 201)));
        query.processes.put(201, process(201, 200, "Helper",
                "/Applications/Sample.app/Contents/Frameworks/Sample Helper"));
        query.processes.put(200, process(200, 1, "Sample",
                "/Applications/Sample.app/Contents/MacOS/Sample"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        ProcessInfo result = resolver.resolve(address("127.0.0.1", 52000), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, result.getLookupStatus());
        Assert.assertEquals(201L, result.getOwnerPid());
        Assert.assertEquals(200L, result.getApplicationPid());
        Assert.assertEquals("Sample", result.getApplicationName());
    }

    @Test
    public void refreshesOnceAfterAnExactMiss() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of());
        query.connections.add(List.of(connection("127.0.0.1", 53000, "127.0.0.1", 9090, 301)));
        query.processes.put(301, process(301, 1, "curl", "/usr/bin/curl"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        ProcessInfo result = resolver.resolve(address("127.0.0.1", 53000), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, result.getLookupStatus());
        Assert.assertEquals(2, query.connectionQueries);
    }

    @Test
    public void startsSnapshotTtlAfterConnectionEnumerationCompletes() throws Exception {
        AtomicLong clock = new AtomicLong(1L);
        FakeSystemQuery query = new FakeSystemQuery() {
            @Override
            public synchronized List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
                clock.addAndGet(90_000_000L);
                return super.queryConnections();
            }
        };
        query.connections.add(List.of(connection("127.0.0.1", 53100, "127.0.0.1", 9090, 311)));
        query.processes.put(311, process(311, 1, "curl", "/usr/bin/curl"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X", clock::get);

        resolver.resolve(address("127.0.0.1", 53100), address("127.0.0.1", 9090));
        clock.set(150_000_001L);
        resolver.resolve(address("127.0.0.1", 53100), address("127.0.0.1", 9090));

        Assert.assertEquals(1, query.connectionQueries);
    }

    @Test
    public void coalescesConcurrentForcedRefreshesForTheSameSnapshotGeneration() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("127.0.0.1", 53200, "127.0.0.1", 9090, 321)));
        query.connections.add(List.of(
                connection("127.0.0.1", 53200, "127.0.0.1", 9090, 321),
                connection("127.0.0.1", 53201, "127.0.0.1", 9090, 322)));
        query.processes.put(321, process(321, 1, "warmup", "/usr/bin/warmup"));
        query.processes.put(322, process(322, 1, "browser", "/Applications/Browser"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");
        resolver.resolve(address("127.0.0.1", 53200), address("127.0.0.1", 9090));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ProcessInfo>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return resolver.resolve(address("127.0.0.1", 53201), address("127.0.0.1", 9090));
                }));
            }
            start.countDown();
            for (Future<ProcessInfo> future : futures) {
                Assert.assertEquals(ProcessInfo.LookupStatus.FOUND,
                        future.get(2, TimeUnit.SECONDS).getLookupStatus());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Assert.assertEquals(2, query.connectionQueries);
        Assert.assertEquals(1, query.processQueryCount(322));
    }

    @Test
    public void doesNotKeepRetryingAfterTheForcedRefreshMisses() {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of());
        query.connections.add(List.of());
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        ProcessInfo result = resolver.resolve(address("127.0.0.1", 53000), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.NOT_FOUND, result.getLookupStatus());
        Assert.assertEquals(2, query.connectionQueries);
    }

    @Test
    public void reportsUnsupportedWithoutQueryingOtherOperatingSystems() {
        FakeSystemQuery query = new FakeSystemQuery();
        OshiProcessInfoResolver resolver = resolver(query, "Linux");

        ProcessInfo result = resolver.resolve(address("127.0.0.1", 53000), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.UNSUPPORTED, result.getLookupStatus());
        Assert.assertEquals(0, query.connectionQueries);
    }

    @Test
    public void matchesAnAcceptedConnectionWhenTheProxyWasBoundToAWildcardAddress() throws Exception {
        OshiProcessInfoResolver.ConnectionRecord connection = connection(
                "127.0.0.1", 54000, "127.0.0.1", 9090, 401);

        OshiProcessInfoResolver.ConnectionRecord result = OshiProcessInfoResolver.findConnection(
                List.of(connection), address("127.0.0.1", 54000), address("0.0.0.0", 9090));

        Assert.assertSame(connection, result);
    }

    @Test
    public void indexedSnapshotPreservesWildcardProxyMatching() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("127.0.0.1", 54100, "127.0.0.1", 9090, 411)));
        query.processes.put(411, process(411, 1, "curl", "/usr/bin/curl"));

        ProcessInfo result = resolver(query, "Mac OS X")
                .resolve(address("127.0.0.1", 54100), address("0.0.0.0", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, result.getLookupStatus());
    }

    @Test
    public void cachesFoundProcessInfoByOwnerPidAndReturnsCopies() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("127.0.0.1", 54200, "127.0.0.1", 9090, 421)));
        query.processes.put(421, process(421, 420, "Helper",
                "/Applications/Sample.app/Contents/Frameworks/Helper"));
        query.processes.put(420, process(420, 1, "Sample",
                "/Applications/Sample.app/Contents/MacOS/Sample"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        ProcessInfo first = resolver.resolve(address("127.0.0.1", 54200), address("127.0.0.1", 9090));
        first.setApplicationName("mutated");
        ProcessInfo second = resolver.resolve(address("127.0.0.1", 54200), address("127.0.0.1", 9090));

        Assert.assertEquals("Sample", second.getApplicationName());
        Assert.assertNotSame(first, second);
        Assert.assertEquals(1, query.processQueryCount(421));
        Assert.assertEquals(1, query.processQueryCount(420));
    }

    @Test
    public void reloadsProcessInfoAfterPidCacheExpires() throws Exception {
        AtomicLong clock = new AtomicLong(1L);
        FakeSystemQuery query = new FakeSystemQuery();
        query.connections.add(List.of(connection("127.0.0.1", 54300, "127.0.0.1", 9090, 431)));
        query.processes.put(431, process(431, 1, "curl", "/usr/bin/curl"));
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X", clock::get);

        resolver.resolve(address("127.0.0.1", 54300), address("127.0.0.1", 9090));
        clock.addAndGet(OshiProcessInfoResolver.PROCESS_CACHE_TTL_NANOS + 1);
        resolver.resolve(address("127.0.0.1", 54300), address("127.0.0.1", 9090));

        Assert.assertEquals(2, query.processQueryCount(431));
    }

    @Test
    public void doesNotCacheMissingProcesses() throws Exception {
        AtomicInteger processQueries = new AtomicInteger();
        OshiProcessInfoResolver.ConnectionRecord connection =
                connection("127.0.0.1", 54400, "127.0.0.1", 9090, 441);
        OshiProcessInfoResolver.SystemQuery query = new OshiProcessInfoResolver.SystemQuery() {
            @Override
            public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
                return List.of(connection);
            }

            @Override
            public OshiProcessInfoResolver.NativeProcess queryProcess(int pid) {
                return processQueries.incrementAndGet() == 1
                        ? null : process(441, 1, "curl", "/usr/bin/curl");
            }
        };
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        ProcessInfo missing = resolver.resolve(address("127.0.0.1", 54400), address("127.0.0.1", 9090));
        ProcessInfo found = resolver.resolve(address("127.0.0.1", 54400), address("127.0.0.1", 9090));

        Assert.assertEquals(ProcessInfo.LookupStatus.NOT_FOUND, missing.getLookupStatus());
        Assert.assertEquals(ProcessInfo.LookupStatus.FOUND, found.getLookupStatus());
        Assert.assertEquals(2, processQueries.get());
    }

    @Test
    public void evictsLeastRecentlyUsedPidWhenCacheReachesItsBound() throws Exception {
        FakeSystemQuery query = new FakeSystemQuery();
        List<OshiProcessInfoResolver.ConnectionRecord> connections = new ArrayList<>();
        for (int i = 0; i <= OshiProcessInfoResolver.PROCESS_CACHE_MAX_ENTRIES; i++) {
            int pid = 1000 + i;
            int port = 55000 + i;
            connections.add(connection("127.0.0.1", port, "127.0.0.1", 9090, pid));
            query.processes.put(pid, process(pid, 1, "process-" + pid, "/tmp/process-" + pid));
        }
        query.connections.add(connections);
        OshiProcessInfoResolver resolver = resolver(query, "Mac OS X");

        for (int i = 0; i <= OshiProcessInfoResolver.PROCESS_CACHE_MAX_ENTRIES; i++) {
            resolver.resolve(address("127.0.0.1", 55000 + i), address("127.0.0.1", 9090));
        }
        resolver.resolve(address("127.0.0.1", 55000), address("127.0.0.1", 9090));

        Assert.assertEquals(2, query.processQueryCount(1000));
        Assert.assertEquals(1, query.processQueryCount(1256));
    }

    @Test
    public void mapsSecurityAndUnsupportedFailuresToStatuses() {
        OshiProcessInfoResolver.SystemQuery denied = new FailingSystemQuery(new SecurityException("denied"));
        OshiProcessInfoResolver.SystemQuery unsupported = new FailingSystemQuery(
                new UnsupportedOperationException("unsupported"));

        Assert.assertEquals(ProcessInfo.LookupStatus.ACCESS_DENIED,
                resolver(denied, "Windows").resolve(address("127.0.0.1", 1), address("127.0.0.1", 2))
                        .getLookupStatus());
        Assert.assertEquals(ProcessInfo.LookupStatus.UNSUPPORTED,
                resolver(unsupported, "Mac OS X").resolve(address("127.0.0.1", 1), address("127.0.0.1", 2))
                        .getLookupStatus());
    }

    @Test
    public void mapsMissingRuntimeDependenciesToUnsupported() {
        OshiProcessInfoResolver.SystemQuery missingDependency = new OshiProcessInfoResolver.SystemQuery() {
            @Override
            public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
                throw new NoClassDefFoundError("oshi/SystemInfo");
            }

            @Override
            public OshiProcessInfoResolver.NativeProcess queryProcess(int pid) {
                throw new NoClassDefFoundError("oshi/SystemInfo");
            }
        };

        ProcessInfo result = resolver(missingDependency, "Mac OS X")
                .resolve(address("127.0.0.1", 1), address("127.0.0.1", 2));

        Assert.assertEquals(ProcessInfo.LookupStatus.UNSUPPORTED, result.getLookupStatus());
    }

    private OshiProcessInfoResolver resolver(OshiProcessInfoResolver.SystemQuery query, String osName) {
        return resolver(query, osName, new AtomicLong(1)::get);
    }

    private OshiProcessInfoResolver resolver(OshiProcessInfoResolver.SystemQuery query, String osName,
                                             LongSupplier nanoTime) {
        return new OshiProcessInfoResolver(query, osName, nanoTime);
    }

    private static InetSocketAddress address(String host, int port) {
        return new InetSocketAddress(host, port);
    }

    private static OshiProcessInfoResolver.ConnectionRecord connection(
            String localHost, int localPort, String remoteHost, int remotePort, int pid) throws Exception {
        return new OshiProcessInfoResolver.ConnectionRecord(
                InetAddress.getByName(localHost).getAddress(), localPort,
                InetAddress.getByName(remoteHost).getAddress(), remotePort, pid);
    }

    private static OshiProcessInfoResolver.NativeProcess process(int pid, int parentPid, String name, String path) {
        return new OshiProcessInfoResolver.NativeProcess(pid, parentPid, name, path);
    }

    private static class FakeSystemQuery implements OshiProcessInfoResolver.SystemQuery {
        private final Deque<List<OshiProcessInfoResolver.ConnectionRecord>> connections = new ArrayDeque<>();
        private final Map<Integer, OshiProcessInfoResolver.NativeProcess> processes = new HashMap<>();
        private final Map<Integer, Integer> processQueries = new HashMap<>();
        private int connectionQueries;

        @Override
        public synchronized List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
            connectionQueries++;
            return connections.size() > 1 ? connections.removeFirst() : connections.getFirst();
        }

        @Override
        public synchronized OshiProcessInfoResolver.NativeProcess queryProcess(int pid) {
            processQueries.merge(pid, 1, Integer::sum);
            return processes.get(pid);
        }

        private synchronized int processQueryCount(int pid) {
            return processQueries.getOrDefault(pid, 0);
        }
    }

    private static class FailingSystemQuery implements OshiProcessInfoResolver.SystemQuery {
        private final RuntimeException failure;

        private FailingSystemQuery(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
            throw failure;
        }

        @Override
        public OshiProcessInfoResolver.NativeProcess queryProcess(int pid) {
            throw failure;
        }
    }
}
