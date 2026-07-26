package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.StatsData;

import java.util.Set;

public record ApplicationGroupOverview(RequestCell.NodeType nodeType,
                                       String applicationName,
                                       String host,
                                       int domainCount,
                                       Set<String> protocols,
                                       Set<Integer> ports,
                                       Set<String> processNames,
                                       Set<Long> ownerPids,
                                       Set<Long> applicationPids,
                                       String executablePath,
                                       Set<ProcessInfo.LookupStatus> lookupStatuses,
                                       StatsData statistics) {
}
