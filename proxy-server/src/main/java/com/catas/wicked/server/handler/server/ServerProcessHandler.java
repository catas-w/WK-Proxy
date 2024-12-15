package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.ProxyHandlerFactory;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.server.handler.client.ClientChannelInitializer;
import com.catas.wicked.server.strategy.StrategyManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


/**
 * send data to target server
 */
@Slf4j
public class ServerProcessHandler extends ChannelInboundHandlerAdapter {

    private boolean isConnected;

    private ApplicationConfig appConfig;

    private ChannelFuture channelFuture;

    private final List<Object> requestList;

    private final MessageQueue messageQueue;

    private StrategyManager strategyManager;

    private final AttributeKey<ProxyRequestInfo> requestInfoAttributeKey =
            AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    private AtomicReference<String> curRequestId;
    public ServerProcessHandler(ApplicationConfig applicationConfig,
                                MessageQueue messageQueue,
                                StrategyManager strategyManager) {
        this.appConfig = applicationConfig;
        this.messageQueue = messageQueue;
        this.strategyManager = strategyManager;
        requestList = new LinkedList<>();
        curRequestId = new AtomicReference<>("initId");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ProxyRequestInfo curRequestInfo = ctx.channel().attr(requestInfoAttributeKey).get();
        if (curRequestInfo == null) {
            log.error("Request info is null");
            ReferenceCountUtil.release(msg);
            return;
        }

        handleProxyData(ctx, msg, curRequestInfo);
    }

    private void handleProxyData(ChannelHandlerContext ctx, Object msg, ProxyRequestInfo requestInfo)  throws Exception {
        boolean newRequest = false;
        String reqId = curRequestId.get();
        if (!StringUtils.equals(reqId, requestInfo.getRequestId())) {
            newRequest = curRequestId.compareAndSet(reqId, requestInfo.getRequestId());
        }
        if (channelFuture == null || newRequest) {
            if (requestInfo.getClientType() == ProxyRequestInfo.ClientType.NORMAL
                    && (!(msg instanceof HttpRequest))) {
                return;
            }

            // TODO: thread safe
            // curRequestId = requestInfo.getRequestId();
            isConnected = false;
            requestInfo.setClientConnected(false);
            Bootstrap bootstrap = new Bootstrap();

            // set external proxyHandler if needed
            ProxyHandler proxyHandler = null;
            if (requestInfo.isUsingExternalProxy()) {
                proxyHandler = ProxyHandlerFactory.getExternalProxyHandler(
                        appConfig.getSettings().getExternalProxy(), WebUtils.getHostname(requestInfo));
                if (proxyHandler != null) {
                    // TODO: bugfix HTTP proxy error - UnresolvedAddressException
                    bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
                }
            }

            bootstrap.group(appConfig.getProxyLoopGroup())
                    .channel(NioSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .handler(new ClientChannelInitializer(appConfig, messageQueue, requestInfo,
                            strategyManager, proxyHandler, ctx.channel()));

            requestList.clear();
            channelFuture = bootstrap.connect(requestInfo.getHost(), requestInfo.getPort());
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    requestInfo.updateClientStatus(ClientStatus.Status.FINISHED);

                    // remote address
                    SocketAddress address = future.channel().remoteAddress();
                    if (address instanceof InetSocketAddress inetSocketAddress) {
                        // System.out.println(inetSocketAddress.getAddress().getHostAddress());
                        requestInfo.setRemoteAddress(inetSocketAddress.getAddress().getHostAddress());
                    }

                    ReferenceCountUtil.retain(msg);
                    future.channel().writeAndFlush(msg);
                    ctx.fireChannelRead(msg);

                    if (!requestList.isEmpty()) {
                        synchronized (requestList) {
                            requestList.forEach(obj -> {
                                ReferenceCountUtil.retain(obj);
                                future.channel().writeAndFlush(obj);
                                ctx.fireChannelRead(obj);
                            });
                            requestList.clear();
                        }
                    }
                    isConnected = true;
                } else {
                    // add error msg, send requestList to postRecorder
                    Throwable cause = future.cause();
                    log.error("Error in creating proxy client channel", cause);
                    ClientStatus.Status targetStatus;
                    if (cause instanceof ClosedChannelException) {
                        targetStatus = ClientStatus.Status.CLOSED;
                    } else if (cause instanceof ConnectTimeoutException) {
                        targetStatus = ClientStatus.Status.TIMEOUT;
                    } else if (cause instanceof SocketException) {
                        targetStatus = ClientStatus.Status.CONNECT_ERR;
                    } else if (cause instanceof UnknownHostException) {
                        targetStatus = ClientStatus.Status.ADDR_NOTFOUND;
                    } else {
                        // javax.net.ssl.SSLPeerUnverifiedException
                        log.error("Unknown client error: ", cause);
                        targetStatus = ClientStatus.Status.UNKNOWN_ERR;
                    }
                    requestInfo.updateClientStatus(targetStatus, cause.getMessage());

                    ctx.fireChannelRead(msg);
                    synchronized (requestList) {
                        requestList.forEach(ctx::fireChannelRead);
                    }

                    HttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT);
                    ctx.writeAndFlush(response);

                    ClientStatus clientStatus = requestInfo.getClientStatus();
                    if (!clientStatus.isSuccess()) {
                        ResponseMessage responseMsg = new ResponseMessage();
                        responseMsg.setRequestId(requestInfo.getRequestId());
                        responseMsg.setStartTime(System.currentTimeMillis());
                        responseMsg.setEndTime(System.currentTimeMillis());
                        responseMsg.setSize(0);
                        responseMsg.setStatus(-1);
                        responseMsg.setReasonPhrase(clientStatus.getStatus().getDesc());
                        messageQueue.pushMsg(Topic.RECORD, responseMsg);
                    }

                    future.channel().close();
                    ctx.channel().close();
                }
            });
        } else {
            synchronized (requestList) {
                if (isConnected) {
                    ReferenceCountUtil.retain(msg);
                    channelFuture.channel().writeAndFlush(msg);
                    ctx.fireChannelRead(msg);
                } else {
                    requestList.add(msg);
                }
            }
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.info("Server channel closing.");
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // TODO channel close - log
        log.error("Server channel unexpected error, closing...", cause);
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        ctx.channel().close();
    }
}
