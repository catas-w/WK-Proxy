package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;

import java.util.Set;

record ApplicationGroupSnapshot(RequestCell.NodeType nodeType,
                                String nodeKey,
                                String applicationName,
                                String host,
                                ProcessInfo processInfo,
                                Set<String> hosts,
                                Set<String> requestIds) {
}
