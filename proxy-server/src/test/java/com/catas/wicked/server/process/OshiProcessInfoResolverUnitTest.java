package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
        return new OshiProcessInfoResolver(query, osName, new AtomicLong(1)::get);
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
        private int connectionQueries;

        @Override
        public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
            connectionQueries++;
            return connections.size() > 1 ? connections.removeFirst() : connections.getFirst();
        }

        @Override
        public OshiProcessInfoResolver.NativeProcess queryProcess(int pid) {
            return processes.get(pid);
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
