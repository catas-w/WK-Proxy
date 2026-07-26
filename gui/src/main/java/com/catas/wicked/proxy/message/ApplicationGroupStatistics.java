package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
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
}
