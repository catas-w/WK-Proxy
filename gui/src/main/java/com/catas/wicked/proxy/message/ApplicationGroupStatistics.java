package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

final class ApplicationGroupStatistics {

    private ApplicationGroupStatistics() {
    }

    static ApplicationGroupOverview aggregate(ApplicationGroupSnapshot snapshot,
                                              Function<String, RequestMessage> requestLookup) {
        if (snapshot == null) {
            return null;
        }

        StatsData statistics = new StatsData();
        statistics.setCountMap(new HashMap<>());
        Set<String> protocols = new LinkedHashSet<>();
        Set<Integer> ports = new LinkedHashSet<>();
        Set<String> processNames = new LinkedHashSet<>();
        Set<Long> ownerPids = new LinkedHashSet<>();
        Set<Long> applicationPids = new LinkedHashSet<>();
        Set<ProcessInfo.LookupStatus> lookupStatuses = new LinkedHashSet<>();
        String executablePath = executable(snapshot.processInfo());

        addProcessInfo(snapshot.processInfo(), processNames, ownerPids, applicationPids, lookupStatuses);
        for (String requestId : snapshot.requestIds()) {
            RequestMessage request = requestLookup.apply(requestId);
            if (request == null) {
                continue;
            }

            statistics.setCount(statistics.getCount() + 1);
            if (StringUtils.isNotBlank(request.getMethod())) {
                HttpMethod method = HttpMethod.valueOf(request.getMethod());
                statistics.getCountMap().merge(method, 1, Integer::sum);
            }
            if (StringUtils.isNotBlank(request.getProtocol())) {
                protocols.add(request.getProtocol().toUpperCase(Locale.ROOT));
            }
            if (request.getRemotePort() > 0) {
                ports.add(request.getRemotePort());
            }

            ProcessInfo processInfo = request.getProcessInfo();
            addProcessInfo(processInfo, processNames, ownerPids, applicationPids, lookupStatuses);
            if (StringUtils.isBlank(executablePath)) {
                executablePath = executable(processInfo);
            }

            long requestStart = request.getStartTime();
            if (requestStart > 0 && (statistics.getStartTime() == null
                    || requestStart < statistics.getStartTime().getTime())) {
                statistics.setStartTime(new Date(requestStart));
            }
            statistics.addRequestsSize(request.getSize());
            statistics.addTotalSize(request.getSize());

            ResponseMessage response = request.getResponse();
            if (response == null) {
                continue;
            }
            statistics.addResponsesSize(response.getSize());
            statistics.addTotalSize(response.getSize());
            long responseEnd = response.getEndTime();
            if (requestStart > 0 && responseEnd >= requestStart) {
                statistics.addTimeCost(responseEnd - requestStart);
                if (statistics.getEndTime() == null || responseEnd > statistics.getEndTime().getTime()) {
                    statistics.setEndTime(new Date(responseEnd));
                }
            }
        }

        if (statistics.getTotalSize() > 0 && statistics.getTimeCost() > 0) {
            statistics.setAverageSpeed((double) statistics.getTotalSize() / statistics.getTimeCost());
        }
        if (lookupStatuses.isEmpty()) {
            lookupStatuses.add(ProcessInfo.LookupStatus.UNKNOWN);
        }

        return new ApplicationGroupOverview(snapshot.nodeType(), snapshot.applicationName(), snapshot.host(),
                snapshot.hosts().size(), immutable(protocols), immutable(ports), immutable(processNames),
                immutable(ownerPids), immutable(applicationPids), executablePath, immutable(lookupStatuses),
                statistics);
    }

    private static void addProcessInfo(ProcessInfo processInfo, Set<String> processNames, Set<Long> ownerPids,
                                       Set<Long> applicationPids,
                                       Set<ProcessInfo.LookupStatus> lookupStatuses) {
        if (processInfo == null) {
            return;
        }
        if (StringUtils.isNotBlank(processInfo.getOwnerProcessName())) {
            processNames.add(processInfo.getOwnerProcessName());
        }
        if (processInfo.getOwnerPid() > 0) {
            ownerPids.add(processInfo.getOwnerPid());
        }
        if (processInfo.getApplicationPid() > 0) {
            applicationPids.add(processInfo.getApplicationPid());
        }
        if (processInfo.getLookupStatus() != null) {
            lookupStatuses.add(processInfo.getLookupStatus());
        }
    }

    private static String executable(ProcessInfo processInfo) {
        return processInfo == null ? null : StringUtils.firstNonBlank(
                processInfo.getApplicationExecutablePath(), processInfo.getOwnerExecutablePath());
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /** Incremental statistics owned by one application or host group. */
    static final class Accumulator {
        private final Map<String, Contribution> requests = new HashMap<>();
        private final Map<HttpMethod, Integer> methods = new HashMap<>();
        private final LinkedHashMap<String, Integer> protocols = new LinkedHashMap<>();
        private final LinkedHashMap<Integer, Integer> ports = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> processNames = new LinkedHashMap<>();
        private final LinkedHashMap<Long, Integer> ownerPids = new LinkedHashMap<>();
        private final LinkedHashMap<Long, Integer> applicationPids = new LinkedHashMap<>();
        private final LinkedHashMap<ProcessInfo.LookupStatus, Integer> statuses = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> executables = new LinkedHashMap<>();
        private final TreeMap<Long, Integer> starts = new TreeMap<>();
        private final TreeMap<Long, Integer> ends = new TreeMap<>();
        private long requestSize;
        private long responseSize;
        private long timeCost;

        void put(RequestMessage request) {
            if (request == null || StringUtils.isBlank(request.getRequestId())) {
                return;
            }
            Contribution previous = requests.remove(request.getRequestId());
            if (previous != null) {
                remove(previous);
            }
            Contribution contribution = Contribution.from(request);
            requests.put(request.getRequestId(), contribution);
            add(contribution);
        }

        void remove(String requestId) {
            Contribution contribution = requests.remove(requestId);
            if (contribution != null) {
                remove(contribution);
            }
        }

        void clear() {
            requests.clear();
            methods.clear();
            protocols.clear();
            ports.clear();
            processNames.clear();
            ownerPids.clear();
            applicationPids.clear();
            statuses.clear();
            executables.clear();
            starts.clear();
            ends.clear();
            requestSize = 0;
            responseSize = 0;
            timeCost = 0;
        }

        ApplicationGroupOverview overview(RequestCell.NodeType nodeType, String applicationName,
                                          String host, int domainCount) {
            StatsData statistics = new StatsData();
            statistics.setCount(requests.size());
            statistics.setCountMap(Collections.unmodifiableMap(new HashMap<>(methods)));
            statistics.setRequestsSize(requestSize);
            statistics.setResponsesSize(responseSize);
            statistics.setTotalSize(requestSize + responseSize);
            statistics.setTimeCost(timeCost);
            statistics.setStartTime(starts.isEmpty() ? null : new Date(starts.firstKey()));
            statistics.setEndTime(ends.isEmpty() ? null : new Date(ends.lastKey()));
            if (statistics.getTotalSize() > 0 && timeCost > 0) {
                statistics.setAverageSpeed((double) statistics.getTotalSize() / timeCost);
            }
            Set<ProcessInfo.LookupStatus> lookupStatuses = keySet(statuses);
            if (lookupStatuses.isEmpty()) {
                lookupStatuses = Set.of(ProcessInfo.LookupStatus.UNKNOWN);
            }
            return new ApplicationGroupOverview(nodeType, applicationName, host, domainCount,
                    keySet(protocols), keySet(ports), keySet(processNames), keySet(ownerPids),
                    keySet(applicationPids), executables.isEmpty() ? null : executables.keySet().iterator().next(),
                    lookupStatuses, statistics);
        }

        private void add(Contribution value) {
            increment(methods, value.method());
            increment(protocols, value.protocol());
            increment(ports, value.port());
            increment(processNames, value.processName());
            increment(ownerPids, value.ownerPid());
            increment(applicationPids, value.applicationPid());
            increment(statuses, value.status());
            increment(executables, value.executable());
            increment(starts, value.start());
            increment(ends, value.end());
            requestSize += value.requestSize();
            responseSize += value.responseSize();
            timeCost += value.timeCost();
        }

        private void remove(Contribution value) {
            decrement(methods, value.method());
            decrement(protocols, value.protocol());
            decrement(ports, value.port());
            decrement(processNames, value.processName());
            decrement(ownerPids, value.ownerPid());
            decrement(applicationPids, value.applicationPid());
            decrement(statuses, value.status());
            decrement(executables, value.executable());
            decrement(starts, value.start());
            decrement(ends, value.end());
            requestSize -= value.requestSize();
            responseSize -= value.responseSize();
            timeCost -= value.timeCost();
        }

        private static <T> void increment(Map<T, Integer> values, T value) {
            if (value != null) {
                values.merge(value, 1, Integer::sum);
            }
        }

        private static <T> void decrement(Map<T, Integer> values, T value) {
            if (value != null) {
                values.computeIfPresent(value, (key, count) -> count == 1 ? null : count - 1);
            }
        }

        private static <T> Set<T> keySet(Map<T, Integer> values) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
        }
    }

    private record Contribution(HttpMethod method, String protocol, Integer port, String processName,
                                Long ownerPid, Long applicationPid, ProcessInfo.LookupStatus status,
                                String executable, Long start, Long end, long requestSize,
                                long responseSize, long timeCost) {
        private static Contribution from(RequestMessage request) {
            HttpMethod method = null;
            try {
                if (StringUtils.isNotBlank(request.getMethod())) {
                    method = HttpMethod.valueOf(request.getMethod());
                }
            } catch (IllegalArgumentException ignored) {
                // Unknown methods remain part of the total count.
            }
            ProcessInfo process = request.getProcessInfo();
            ResponseMessage response = request.getResponse();
            long start = Math.max(0, request.getStartTime());
            long end = response == null ? 0 : Math.max(0, response.getEndTime());
            long duration = start > 0 && end >= start ? end - start : 0;
            return new Contribution(method,
                    StringUtils.isBlank(request.getProtocol()) ? null
                            : request.getProtocol().toUpperCase(Locale.ROOT),
                    request.getRemotePort() > 0 ? request.getRemotePort() : null,
                    process == null ? null : StringUtils.trimToNull(process.getOwnerProcessName()),
                    process != null && process.getOwnerPid() > 0 ? process.getOwnerPid() : null,
                    process != null && process.getApplicationPid() > 0 ? process.getApplicationPid() : null,
                    process == null ? null : process.getLookupStatus(),
                    ApplicationGroupStatistics.executable(process),
                    start > 0 ? start : null, duration > 0 ? end : null,
                    Math.max(0, request.getSize()), response == null ? 0 : Math.max(0, response.getSize()), duration);
        }
    }
}
