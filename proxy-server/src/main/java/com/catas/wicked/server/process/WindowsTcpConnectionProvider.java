package com.catas.wicked.server.process;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.IPHlpAPI;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
final class WindowsTcpConnectionProvider implements TcpConnectionProvider {

    private static final int MAX_BUFFER_RETRIES = 3;
    static final int IPV4_FAMILY = 2;
    static final int IPV6_FAMILY = 23;
    static final int OWNER_PID_ALL = 5;

    private final TcpTableApi api;
    private final NativeTableParser nativeTableParser;

    WindowsTcpConnectionProvider() {
        this((table, size, order, addressFamily, tableClass, reserved) ->
                        IPHlpAPI.INSTANCE.GetExtendedTcpTable(
                                table, size, order, addressFamily, tableClass, reserved),
                new JnaNativeTableParser());
    }

    WindowsTcpConnectionProvider(TcpTableApi api) {
        this(api, new JnaNativeTableParser());
    }

    WindowsTcpConnectionProvider(TcpTableApi api, NativeTableParser nativeTableParser) {
        this.api = api;
        this.nativeTableParser = nativeTableParser;
    }

    @Override
    public List<OshiProcessInfoResolver.ConnectionRecord> queryConnections() {
        long startedAt = System.nanoTime();
        List<OshiProcessInfoResolver.ConnectionRecord> connections = new ArrayList<>();
        List<OshiProcessInfoResolver.ConnectionRecord> ipv4 = queryIpv4();
        long ipv4CompletedAt = System.nanoTime();
        List<OshiProcessInfoResolver.ConnectionRecord> ipv6 = queryIpv6();
        long completedAt = System.nanoTime();
        connections.addAll(ipv4);
        connections.addAll(ipv6);
        log.debug("Windows TCP owner tables queried: ipv4={}, ipv6={}, ipv4Ms={}, ipv6Ms={}, totalMs={}",
                ipv4.size(), ipv6.size(), TimeUnit.NANOSECONDS.toMillis(ipv4CompletedAt - startedAt),
                TimeUnit.NANOSECONDS.toMillis(completedAt - ipv4CompletedAt),
                TimeUnit.NANOSECONDS.toMillis(completedAt - startedAt));
        return connections;
    }

    private List<OshiProcessInfoResolver.ConnectionRecord> queryIpv4() {
        return queryTable(IPV4_FAMILY, nativeTableParser::parseIpv4);
    }

    private List<OshiProcessInfoResolver.ConnectionRecord> queryIpv6() {
        return queryTable(IPV6_FAMILY, nativeTableParser::parseIpv6);
    }

    private static final class JnaNativeTableParser implements NativeTableParser {
        @Override
        public List<OshiProcessInfoResolver.ConnectionRecord> parseIpv4(Pointer pointer) {
            IPHlpAPI.MIB_TCPTABLE_OWNER_PID table = new IPHlpAPI.MIB_TCPTABLE_OWNER_PID(pointer);
            List<OshiProcessInfoResolver.ConnectionRecord> result = new ArrayList<>(table.dwNumEntries);
            for (IPHlpAPI.MIB_TCPROW_OWNER_PID row : table.table) {
                result.add(new OshiProcessInfoResolver.ConnectionRecord(
                        ipv4Bytes(row.dwLocalAddr), port(row.dwLocalPort),
                        ipv4Bytes(row.dwRemoteAddr), port(row.dwRemotePort), row.dwOwningPid));
            }
            return result;
        }

        @Override
        public List<OshiProcessInfoResolver.ConnectionRecord> parseIpv6(Pointer pointer) {
            IPHlpAPI.MIB_TCP6TABLE_OWNER_PID table = new IPHlpAPI.MIB_TCP6TABLE_OWNER_PID(pointer);
            List<OshiProcessInfoResolver.ConnectionRecord> result = new ArrayList<>(table.dwNumEntries);
            for (IPHlpAPI.MIB_TCP6ROW_OWNER_PID row : table.table) {
                result.add(new OshiProcessInfoResolver.ConnectionRecord(
                        Arrays.copyOf(row.LocalAddr, row.LocalAddr.length), port(row.dwLocalPort),
                        Arrays.copyOf(row.RemoteAddr, row.RemoteAddr.length), port(row.dwRemotePort),
                        row.dwOwningPid));
            }
            return result;
        }
    }

    private List<OshiProcessInfoResolver.ConnectionRecord> queryTable(int addressFamily,
                                                                      TableParser parser) {
        IntByReference size = new IntByReference();
        int status = api.getExtendedTcpTable(null, size, false, addressFamily, OWNER_PID_ALL, 0);
        if (status != WinError.ERROR_INSUFFICIENT_BUFFER && status != WinError.NO_ERROR) {
            throw new IllegalStateException("GetExtendedTcpTable size query failed: " + status);
        }
        if (size.getValue() <= 0) {
            return List.of();
        }

        for (int attempt = 0; attempt < MAX_BUFFER_RETRIES; attempt++) {
            try (Memory buffer = new Memory(size.getValue())) {
                status = api.getExtendedTcpTable(buffer, size, false, addressFamily, OWNER_PID_ALL, 0);
                if (status == WinError.ERROR_INSUFFICIENT_BUFFER) {
                    continue;
                }
                if (status != WinError.NO_ERROR) {
                    throw new IllegalStateException("GetExtendedTcpTable failed: " + status);
                }
                return parser.parse(buffer);
            }
        }
        throw new IllegalStateException("GetExtendedTcpTable buffer changed repeatedly");
    }

    static int port(int networkOrderPort) {
        return Short.toUnsignedInt(Short.reverseBytes((short) networkOrderPort));
    }

    static byte[] ipv4Bytes(int address) {
        return new byte[]{
                (byte) address,
                (byte) (address >>> 8),
                (byte) (address >>> 16),
                (byte) (address >>> 24)
        };
    }

    @FunctionalInterface
    interface TcpTableApi {
        int getExtendedTcpTable(Pointer table, IntByReference size, boolean order,
                                int addressFamily, int tableClass, int reserved);
    }

    interface NativeTableParser {
        List<OshiProcessInfoResolver.ConnectionRecord> parseIpv4(Pointer pointer);

        List<OshiProcessInfoResolver.ConnectionRecord> parseIpv6(Pointer pointer);
    }

    @FunctionalInterface
    private interface TableParser {
        List<OshiProcessInfoResolver.ConnectionRecord> parse(Pointer pointer);
    }
}
