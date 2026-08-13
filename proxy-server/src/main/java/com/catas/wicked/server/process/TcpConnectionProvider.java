package com.catas.wicked.server.process;

import java.util.List;

interface TcpConnectionProvider {

    List<OshiProcessInfoResolver.ConnectionRecord> queryConnections();
}
