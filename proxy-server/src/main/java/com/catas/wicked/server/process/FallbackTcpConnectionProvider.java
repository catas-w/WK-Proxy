package com.catas.wicked.server.process;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
final class FallbackTcpConnectionProvider implements TcpConnectionProvider {

    private final TcpConnectionProvider primary;
    private final TcpConnectionProvider fallback;
    private final AtomicBoolean fallbackLogged = new AtomicBoolean();

    FallbackTcpConnectionProvider(TcpConnectionProvider primary, TcpConnectionProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
        try {
            return primary.queryConnections();
        } catch (RuntimeException | LinkageError exception) {
            if (fallbackLogged.compareAndSet(false, true)) {
                log.warn("Windows TCP owner lookup failed; falling back to OSHI: {}", exception.toString());
            }
            return fallback.queryConnections();
        }
    }
}
