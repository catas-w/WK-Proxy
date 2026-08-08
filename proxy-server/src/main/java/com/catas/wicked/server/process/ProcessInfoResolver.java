package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;

import java.net.InetSocketAddress;

public interface ProcessInfoResolver {

    ProcessInfo resolve(InetSocketAddress clientAddress, InetSocketAddress proxyAddress);

    default void warmUp() {
        // Most resolvers have no expensive runtime initialization.
    }
}
