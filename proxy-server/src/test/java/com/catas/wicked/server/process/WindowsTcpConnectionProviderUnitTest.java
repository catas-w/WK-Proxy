package com.catas.wicked.server.process;

import com.sun.jna.platform.win32.WinError;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowsTcpConnectionProviderUnitTest {

    @Test
    public void defersLoadingTheWindowsLibraryUntilTheFirstQuery() {
        Assert.assertNotNull(new WindowsTcpConnectionProvider());
    }

    @Test
    public void queriesOnlyTcpIpv4AndIpv6OwnerTables() {
        List<Integer> families = new ArrayList<>();
        List<Integer> tableClasses = new ArrayList<>();
        WindowsTcpConnectionProvider provider = new WindowsTcpConnectionProvider(
                (table, size, order, family, tableClass, reserved) -> {
                    families.add(family);
                    tableClasses.add(tableClass);
                    if (table == null) {
                        size.setValue(Integer.BYTES);
                        return WinError.ERROR_INSUFFICIENT_BUFFER;
                    }
                    table.setInt(0, 0);
                    return WinError.NO_ERROR;
                }, emptyParser());

        List<OshiProcessInfoResolver.ConnectionRecord> result = provider.queryConnections();

        Assert.assertTrue(result.isEmpty());
        Assert.assertEquals(List.of(WindowsTcpConnectionProvider.IPV4_FAMILY,
                WindowsTcpConnectionProvider.IPV4_FAMILY,
                WindowsTcpConnectionProvider.IPV6_FAMILY,
                WindowsTcpConnectionProvider.IPV6_FAMILY), families);
        Assert.assertEquals(List.of(5, 5, 5, 5), tableClasses);
    }

    @Test
    public void retriesWhenTheTcpTableGrowsDuringRead() {
        AtomicInteger ipv4BufferReads = new AtomicInteger();
        WindowsTcpConnectionProvider provider = new WindowsTcpConnectionProvider(
                (table, size, order, family, tableClass, reserved) -> {
                    if (table == null) {
                        size.setValue(Integer.BYTES);
                        return WinError.ERROR_INSUFFICIENT_BUFFER;
                    }
                    if (family == WindowsTcpConnectionProvider.IPV4_FAMILY
                            && ipv4BufferReads.getAndIncrement() == 0) {
                        size.setValue(Integer.BYTES * 2);
                        return WinError.ERROR_INSUFFICIENT_BUFFER;
                    }
                    table.setInt(0, 0);
                    return WinError.NO_ERROR;
                }, emptyParser());

        Assert.assertTrue(provider.queryConnections().isEmpty());
        Assert.assertEquals(2, ipv4BufferReads.get());
    }

    @Test
    public void convertsWindowsAddressAndPortByteOrder() {
        Assert.assertArrayEquals(new byte[]{127, 0, 0, 1},
                WindowsTcpConnectionProvider.ipv4Bytes(0x0100007f));
        Assert.assertEquals(9966, WindowsTcpConnectionProvider.port(0xee26));
    }

    @Test
    public void fallsBackOnlyWhenThePrimaryProviderFails() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        TcpConnectionProvider fallback = () -> {
            fallbackCalls.incrementAndGet();
            return List.of();
        };

        Assert.assertTrue(new FallbackTcpConnectionProvider(
                () -> List.of(), fallback).queryConnections().isEmpty());
        Assert.assertEquals(0, fallbackCalls.get());

        Assert.assertTrue(new FallbackTcpConnectionProvider(
                () -> { throw new IllegalStateException("native failure"); }, fallback)
                .queryConnections().isEmpty());
        Assert.assertEquals(1, fallbackCalls.get());
    }

    private static WindowsTcpConnectionProvider.NativeTableParser emptyParser() {
        return new WindowsTcpConnectionProvider.NativeTableParser() {
            @Override
            public List<OshiProcessInfoResolver.ConnectionRecord> parseIpv4(com.sun.jna.Pointer pointer) {
                return List.of();
            }

            @Override
            public List<OshiProcessInfoResolver.ConnectionRecord> parseIpv6(com.sun.jna.Pointer pointer) {
                return List.of();
            }
        };
    }
}
