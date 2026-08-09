package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.RequestMessage;
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
import io.netty.channel.ChannelOption;
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

import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
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
                ctx.fireChannelRead(msg);
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
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, appConfig.getSettings().getConnectTimeout() * 1000)
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
                    } else if (cause instanceof ConnectTimeoutException
                            || (cause.getMessage() != null && cause.getMessage().contains("timed out"))) {
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
                        requestList.clear();
                    }

                    HttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT);
                    ctx.writeAndFlush(response);

                    ClientStatus clientStatus = requestInfo.getClientStatus();
                    if (!clientStatus.isSuccess()) {
                        ResponseMessage responseMsg = new ResponseMessage();
                        responseMsg.setRequestId(requestInfo.getRequestId());
                        // responseMsg.setStartTime(System.currentTimeMillis());
                        // responseMsg.setEndTime(System.currentTimeMillis());
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
        releasePendingRequests();
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SSLHandshakeException handshakeException = findCause(cause, SSLHandshakeException.class);
        if (handshakeException != null) {
            ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();
            if (requestInfo != null) {
                recordSslHandshakeFailure(requestInfo, handshakeException);
                log.warn("Client TLS handshake failed for {}:{}, closing channel: {}",
                        requestInfo.getHost(), requestInfo.getPort(), handshakeException.getMessage());
            } else {
                log.warn("Client TLS handshake failed, closing channel: {}", handshakeException.getMessage());
            }
            log.debug("Client TLS handshake failure", cause);
        } else {
            log.error("Server channel unexpected error, closing...", cause);
        }
        releasePendingRequests();
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        ctx.channel().close();
    }

    private void recordSslHandshakeFailure(ProxyRequestInfo requestInfo,
                                           SSLHandshakeException cause) {
        requestInfo.updateClientStatus(ClientStatus.Status.SSL_HANDSHAKE_ERR, cause.getMessage());
        requestInfo.updateRequestTime();
        if (!requestInfo.isRecording() || messageQueue == null) {
            return;
        }

        if (!requestInfo.isHasSentRequestMsg()) {
            RequestMessage requestMessage = new RequestMessage(
                    WebUtils.getHostname(requestInfo) + "/" + ProxyConstant.UNPARSED_ALIAS);
            requestMessage.setRequestId(requestInfo.getRequestId());
            requestMessage.setMethod("-");
            requestMessage.setHeaders(new LinkedHashMap<>());
            requestMessage.setEncrypted(true);
            requestMessage.setStartTime(requestInfo.getRequestStartTime());
            requestMessage.setEndTime(requestInfo.getRequestEndTime());
            requestMessage.setSize(requestInfo.getRequestSize());
            requestMessage.setRemoteHost(requestInfo.getHost());
            requestMessage.setRemotePort(requestInfo.getPort());
            requestMessage.setRemoteAddress(requestInfo.getRemoteAddress());
            requestMessage.setLocalAddress(requestInfo.getLocalAddress());
            requestMessage.setLocalPort(requestInfo.getLocalPort());
            requestMessage.setClientStatus(requestInfo.getClientStatus().copy());
            requestMessage.setProcessInfo(requestInfo.getProcessInfo());
            messageQueue.pushMsg(Topic.RECORD, requestMessage);
            requestInfo.setHasSentRequestMsg(true);
        }

        if (!requestInfo.isHasSentRespMsg()) {
            requestInfo.updateResponseTime();
            ResponseMessage responseMessage = new ResponseMessage();
            responseMessage.setRequestId(requestInfo.getRequestId());
            responseMessage.setStartTime(requestInfo.getResponseStartTime());
            responseMessage.setEndTime(requestInfo.getResponseEndTime());
            responseMessage.setSize(0);
            responseMessage.setStatus(-1);
            responseMessage.setReasonPhrase(ClientStatus.Status.SSL_HANDSHAKE_ERR.getDesc());
            messageQueue.pushMsg(Topic.RECORD, responseMessage);
            requestInfo.setHasSentRespMsg(true);
        }
    }

    private static <T extends Throwable> T findCause(Throwable cause, Class<T> type) {
        Throwable current = cause;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private void releasePendingRequests() {
        synchronized (requestList) {
            requestList.forEach(ReferenceCountUtil::release);
            requestList.clear();
        }
    }
}
