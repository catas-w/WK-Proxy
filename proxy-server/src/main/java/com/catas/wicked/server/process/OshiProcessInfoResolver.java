package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.util.SystemUtils;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

@Slf4j
@Singleton
public class OshiProcessInfoResolver implements ProcessInfoResolver {

    static final long SNAPSHOT_TTL_NANOS = 100_000_000L;
    static final long PROCESS_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);
    static final int PROCESS_CACHE_MAX_ENTRIES = 256;

    private final SystemQuery systemQuery;
    private final String osName;
    private final LongSupplier nanoTime;
    private final Object snapshotLock = new Object();
    private final Object processCacheLock = new Object();
    private final AtomicBoolean linkageFailureLogged = new AtomicBoolean();
    private final Map<Integer, Object> processLookupLocks = new ConcurrentHashMap<>();
    private final LinkedHashMap<Integer, CachedProcessInfo> processCache =
            new LinkedHashMap<>(16, 0.75f, true);

    private volatile ConnectionSnapshot snapshot = ConnectionSnapshot.empty();

    public OshiProcessInfoResolver() {
        this(new OshiSystemQuery(), SystemUtils.OS_NAME, System::nanoTime);
    }

    OshiProcessInfoResolver(SystemQuery systemQuery, String osName, LongSupplier nanoTime) {
        this.systemQuery = systemQuery;
        this.osName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        this.nanoTime = nanoTime;
    }

    @Override
    public ProcessInfo resolve(InetSocketAddress clientAddress, InetSocketAddress proxyAddress) {
        if (!osName.contains("mac") && !osName.contains("win")) {
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.UNSUPPORTED);
        }
        if (!isResolved(clientAddress) || !isResolved(proxyAddress)) {
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.NOT_FOUND);
        }
        try {
            ConnectionSnapshot current = getSnapshot(false, -1L);
            ConnectionRecord connection = current.findConnection(clientAddress, proxyAddress);
            if (connection == null) {
                current = getSnapshot(true, current.generation());
                connection = current.findConnection(clientAddress, proxyAddress);
            }
            if (connection == null || connection.owningProcessId() <= 0) {
                return ProcessInfo.withStatus(ProcessInfo.LookupStatus.NOT_FOUND);
            }
            return resolveProcessInfo(connection.owningProcessId());
        } catch (UnsupportedOperationException exception) {
            log.debug("Process lookup is unsupported", exception);
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.UNSUPPORTED);
        } catch (SecurityException exception) {
            log.debug("Process lookup access was denied", exception);
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.ACCESS_DENIED);
        } catch (LinkageError error) {
            if (linkageFailureLogged.compareAndSet(false, true)) {
                log.warn("Process lookup is unavailable: {}", error.toString());
            }
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.UNSUPPORTED);
        } catch (Exception exception) {
            log.warn("Unable to resolve the source process for {} -> {}", clientAddress, proxyAddress, exception);
            return ProcessInfo.withStatus(ProcessInfo.LookupStatus.ERROR);
        }
    }

    private ConnectionSnapshot getSnapshot(boolean forceRefresh, long observedGeneration) {
        long now = nanoTime.getAsLong();
        ConnectionSnapshot current = snapshot;
        if (!forceRefresh && current.isFresh(now)) {
            log.debug("Process connection snapshot hit, generation={}", current.generation());
            return current;
        }
        synchronized (snapshotLock) {
            current = snapshot;
            now = nanoTime.getAsLong();
            if (forceRefresh && current.generation() != observedGeneration) {
                log.debug("Reusing concurrently refreshed process connection snapshot, generation={}",
                        current.generation());
                return current;
            }
            if (!forceRefresh && current.isFresh(now)) {
                log.debug("Process connection snapshot hit after lock, generation={}", current.generation());
                return current;
            }
            long startedAt = now;
            List<ConnectionRecord> connections = List.copyOf(systemQuery.queryConnections());
            long completedAt = nanoTime.getAsLong();
            ConnectionSnapshot refreshed = ConnectionSnapshot.create(
                    connections, completedAt, current.generation() + 1);
            snapshot = refreshed;
            log.debug("Refreshed process connection snapshot: generation={}, forced={}, connections={}, elapsedMs={}",
                    refreshed.generation(), forceRefresh, connections.size(),
                    TimeUnit.NANOSECONDS.toMillis(completedAt - startedAt));
            return refreshed;
        }
    }

    private ProcessInfo resolveProcessInfo(int ownerPid) {
        ProcessInfo cached = getCachedProcessInfo(ownerPid);
        if (cached != null) {
            log.debug("Process info cache hit, pid={}", ownerPid);
            return cached;
        }

        Object processLock = processLookupLocks.computeIfAbsent(ownerPid, ignored -> new Object());
        try {
            synchronized (processLock) {
                cached = getCachedProcessInfo(ownerPid);
                if (cached != null) {
                    log.debug("Process info cache hit after lock, pid={}", ownerPid);
                    return cached;
                }
                NativeProcess owner = systemQuery.queryProcess(ownerPid);
                if (owner == null) {
                    return ProcessInfo.withStatus(ProcessInfo.LookupStatus.NOT_FOUND);
                }
                ProcessInfo processInfo = toProcessInfo(owner);
                cacheProcessInfo(ownerPid, processInfo);
                return copyProcessInfo(processInfo);
            }
        } finally {
            processLookupLocks.remove(ownerPid, processLock);
        }
    }

    private ProcessInfo getCachedProcessInfo(int ownerPid) {
        long now = nanoTime.getAsLong();
        synchronized (processCacheLock) {
            CachedProcessInfo cached = processCache.get(ownerPid);
            if (cached == null) {
                return null;
            }
            if (now - cached.cachedAtNanos() >= PROCESS_CACHE_TTL_NANOS) {
                processCache.remove(ownerPid);
                return null;
            }
            return copyProcessInfo(cached.processInfo());
        }
    }

    private void cacheProcessInfo(int ownerPid, ProcessInfo processInfo) {
        if (processInfo == null || processInfo.getLookupStatus() != ProcessInfo.LookupStatus.FOUND) {
            return;
        }
        long now = nanoTime.getAsLong();
        synchronized (processCacheLock) {
            Iterator<Map.Entry<Integer, CachedProcessInfo>> iterator = processCache.entrySet().iterator();
            while (iterator.hasNext()) {
                CachedProcessInfo cached = iterator.next().getValue();
                if (now - cached.cachedAtNanos() >= PROCESS_CACHE_TTL_NANOS) {
                    iterator.remove();
                }
            }
            processCache.put(ownerPid, new CachedProcessInfo(copyProcessInfo(processInfo), now));
            while (processCache.size() > PROCESS_CACHE_MAX_ENTRIES) {
                Iterator<Integer> keys = processCache.keySet().iterator();
                keys.next();
                keys.remove();
            }
        }
    }

    private static ProcessInfo copyProcessInfo(ProcessInfo source) {
        return ProcessInfo.builder()
                .ownerPid(source.getOwnerPid())
                .ownerProcessName(source.getOwnerProcessName())
                .ownerExecutablePath(source.getOwnerExecutablePath())
                .applicationPid(source.getApplicationPid())
                .applicationName(source.getApplicationName())
                .applicationExecutablePath(source.getApplicationExecutablePath())
                .lookupStatus(source.getLookupStatus())
                .build();
    }

    private ProcessInfo toProcessInfo(NativeProcess owner) {
        NativeProcess application = resolveApplication(owner);
        return ProcessInfo.builder()
                .ownerPid(owner.pid())
                .ownerProcessName(owner.name())
                .ownerExecutablePath(emptyToNull(owner.path()))
                .applicationPid(application.pid())
                .applicationName(applicationName(application))
                .applicationExecutablePath(emptyToNull(application.path()))
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
    }

    private NativeProcess resolveApplication(NativeProcess owner) {
        if (osName.contains("mac")) {
            return resolveMacApplication(owner);
        }
        if (osName.contains("win")) {
            return resolveWindowsApplication(owner);
        }
        return owner;
    }

    private NativeProcess resolveMacApplication(NativeProcess owner) {
        String bundlePath = appBundlePath(owner.path());
        if (bundlePath == null) {
            return owner;
        }
        NativeProcess application = owner;
        NativeProcess current = owner;
        while (current.parentPid() > 0) {
            NativeProcess parent = systemQuery.queryProcess(current.parentPid());
            if (parent == null || !isInsideBundle(parent.path(), bundlePath)) {
                break;
            }
            application = parent;
            current = parent;
        }
        return application;
    }

    private NativeProcess resolveWindowsApplication(NativeProcess owner) {
        String ownerPath = normalizePath(owner.path());
        if (ownerPath == null) {
            return owner;
        }
        NativeProcess application = owner;
        NativeProcess current = owner;
        while (current.parentPid() > 0) {
            NativeProcess parent = systemQuery.queryProcess(current.parentPid());
            if (parent == null || !ownerPath.equals(normalizePath(parent.path()))) {
                break;
            }
            application = parent;
            current = parent;
        }
        return application;
    }

    private String applicationName(NativeProcess application) {
        if (osName.contains("mac")) {
            String bundlePath = appBundlePath(application.path());
            if (bundlePath != null) {
                String fileName = Path.of(bundlePath).getFileName().toString();
                return fileName.substring(0, fileName.length() - ".app".length());
            }
        }
        return application.name();
    }

    static ConnectionRecord findConnection(List<ConnectionRecord> connections,
                                           InetSocketAddress clientAddress,
                                           InetSocketAddress proxyAddress) {
        for (ConnectionRecord connection : connections) {
            if (connection.localPort() == clientAddress.getPort()
                    && connection.remotePort() == proxyAddress.getPort()
                    && addressMatches(connection.localAddress(), clientAddress.getAddress())
                    && addressMatches(connection.remoteAddress(), proxyAddress.getAddress())) {
                return connection;
            }
        }
        return null;
    }

    static boolean addressMatches(byte[] candidateBytes, InetAddress expected) {
        if (candidateBytes == null || candidateBytes.length == 0 || expected == null) {
            return false;
        }
        try {
            InetAddress candidate = InetAddress.getByAddress(candidateBytes);
            if (expected.isAnyLocalAddress()) {
                return true;
            }
            if (candidate.equals(expected) || (candidate.isLoopbackAddress() && expected.isLoopbackAddress())) {
                return true;
            }
            byte[] candidateV4 = ipv4Bytes(candidate.getAddress());
            byte[] expectedV4 = ipv4Bytes(expected.getAddress());
            return candidateV4 != null && expectedV4 != null && java.util.Arrays.equals(candidateV4, expectedV4);
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static byte[] ipv4Bytes(byte[] bytes) {
        if (bytes.length == 4) {
            return bytes;
        }
        if (bytes.length == 16) {
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    return null;
                }
            }
            if (bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                return java.util.Arrays.copyOfRange(bytes, 12, 16);
            }
        }
        return null;
    }

    private static boolean isResolved(InetSocketAddress address) {
        return address != null && !address.isUnresolved() && address.getAddress() != null;
    }

    private static String appBundlePath(String executablePath) {
        if (executablePath == null) {
            return null;
        }
        String normalized = executablePath.replace('\\', '/');
        int end = normalized.toLowerCase(Locale.ROOT).indexOf(".app/contents/");
        return end < 0 ? null : normalized.substring(0, end + ".app".length());
    }

    private static boolean isInsideBundle(String executablePath, String bundlePath) {
        if (executablePath == null) {
            return false;
        }
        String normalized = executablePath.replace('\\', '/');
        return normalized.equals(bundlePath) || normalized.startsWith(bundlePath + "/");
    }

    private static String normalizePath(String path) {
        String value = emptyToNull(path);
        return value == null ? null : value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    interface SystemQuery {
        List<ConnectionRecord> queryConnections();

        NativeProcess queryProcess(int pid);
    }

    static final class OshiSystemQuery implements SystemQuery {
        private volatile OperatingSystem operatingSystem;
        private volatile InternetProtocolStats internetProtocolStats;

        @Override
        public List<ConnectionRecord> queryConnections() {
            return internetProtocolStats().getConnections().stream()
                    .filter(connection -> connection.getType().startsWith("tcp"))
                    .map(connection -> new ConnectionRecord(
                            connection.getLocalAddress(), connection.getLocalPort(),
                            connection.getForeignAddress(), connection.getForeignPort(),
                            connection.getowningProcessId()))
                    .toList();
        }

        @Override
        public NativeProcess queryProcess(int pid) {
            OSProcess process = operatingSystem().getProcess(pid);
            return process == null ? null : new NativeProcess(
                    process.getProcessID(), process.getParentProcessID(), process.getName(), process.getPath());
        }

        private OperatingSystem operatingSystem() {
            OperatingSystem current = operatingSystem;
            if (current == null) {
                synchronized (this) {
                    current = operatingSystem;
                    if (current == null) {
                        current = new SystemInfo().getOperatingSystem();
                        operatingSystem = current;
                    }
                }
            }
            return current;
        }

        private InternetProtocolStats internetProtocolStats() {
            InternetProtocolStats current = internetProtocolStats;
            if (current == null) {
                synchronized (this) {
                    current = internetProtocolStats;
                    if (current == null) {
                        current = operatingSystem().getInternetProtocolStats();
                        internetProtocolStats = current;
                    }
                }
            }
            return current;
        }
    }

    record ConnectionRecord(byte[] localAddress, int localPort, byte[] remoteAddress,
                            int remotePort, int owningProcessId) {
    }

    record NativeProcess(int pid, int parentPid, String name, String path) {
    }

    private record PortPair(int localPort, int remotePort) {
    }

    private record CachedProcessInfo(ProcessInfo processInfo, long cachedAtNanos) {
    }

    private record ConnectionSnapshot(Map<PortPair, List<ConnectionRecord>> connectionsByPort,
                                      long fetchedAtNanos,
                                      long generation) {

        private static ConnectionSnapshot empty() {
            return new ConnectionSnapshot(Map.of(), 0L, 0L);
        }

        private static ConnectionSnapshot create(List<ConnectionRecord> connections,
                                                 long fetchedAtNanos,
                                                 long generation) {
            Map<PortPair, List<ConnectionRecord>> mutableIndex = new LinkedHashMap<>();
            for (ConnectionRecord connection : connections) {
                PortPair key = new PortPair(connection.localPort(), connection.remotePort());
                mutableIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(connection);
            }
            Map<PortPair, List<ConnectionRecord>> immutableIndex = new LinkedHashMap<>();
            mutableIndex.forEach((key, value) -> immutableIndex.put(key, List.copyOf(value)));
            return new ConnectionSnapshot(Map.copyOf(immutableIndex), fetchedAtNanos, generation);
        }

        private boolean isFresh(long now) {
            return generation > 0 && now - fetchedAtNanos < SNAPSHOT_TTL_NANOS;
        }

        private ConnectionRecord findConnection(InetSocketAddress clientAddress,
                                                InetSocketAddress proxyAddress) {
            List<ConnectionRecord> candidates = connectionsByPort.getOrDefault(
                    new PortPair(clientAddress.getPort(), proxyAddress.getPort()), List.of());
            return OshiProcessInfoResolver.findConnection(candidates, clientAddress, proxyAddress);
        }
    }

}
