package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.server.process.ProcessInfoLookupService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

public class ProcessInfoLookupHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<ProcessInfo> PROCESS_INFO_KEY =
            AttributeKey.valueOf(ProxyConstant.PROCESS_INFO);
    public static final AttributeKey<CompletableFuture<ProcessInfo>> PROCESS_INFO_FUTURE_KEY =
            AttributeKey.valueOf(ProxyConstant.PROCESS_INFO_FUTURE);

    private final ProcessInfoLookupService lookupService;

    public ProcessInfoLookupHandler(ProcessInfoLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ProcessInfo initial = ProcessInfo.unknown();
        ctx.channel().attr(PROCESS_INFO_KEY).set(initial);

        CompletableFuture<ProcessInfo> future;
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        SocketAddress localAddress = ctx.channel().localAddress();
        if (remoteAddress instanceof InetSocketAddress clientAddress
                && localAddress instanceof InetSocketAddress proxyAddress) {
            future = lookupService.lookup(clientAddress, proxyAddress);
        } else {
            future = CompletableFuture.completedFuture(initial);
        }
        ctx.channel().attr(PROCESS_INFO_FUTURE_KEY).set(future);
        future.whenComplete((processInfo, throwable) -> ctx.executor().execute(() -> {
            if (ctx.channel().attr(PROCESS_INFO_FUTURE_KEY).get() != future) {
                return;
            }
            ProcessInfo finalInfo = throwable == null && processInfo != null
                    ? processInfo : ProcessInfo.withStatus(ProcessInfo.LookupStatus.ERROR);
            ctx.channel().attr(PROCESS_INFO_KEY).set(finalInfo);
        }));

        super.channelActive(ctx);
    }

    public static void override(ChannelHandlerContext ctx, ProcessInfo processInfo) {
        CompletableFuture<ProcessInfo> previous = ctx.channel().attr(PROCESS_INFO_FUTURE_KEY).getAndSet(
                CompletableFuture.completedFuture(processInfo));
        ctx.channel().attr(PROCESS_INFO_KEY).set(processInfo);
        if (previous != null && !previous.isDone()) {
            previous.cancel(true);
        }
    }
}
