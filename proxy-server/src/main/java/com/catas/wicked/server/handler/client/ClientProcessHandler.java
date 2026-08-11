package com.catas.wicked.server.handler.client;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.ProxyRequestTiming;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.server.client.UpstreamSslHandlerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ClientProcessHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;

    private final MessageQueue messageQueue;

    private final AttributeKey<ProxyRequestInfo> requestInfoAttributeKey =
            AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    private boolean terminalErrorHandled;

    private boolean tlsFailureLogged;

    public ClientProcessHandler(Channel clientChannel, MessageQueue messageQueue) {
        this.clientChannel = clientChannel;
        this.messageQueue = messageQueue;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!clientChannel.isOpen()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        // refresh timing & size
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();
        ProxyRequestTiming timing = null;
        if (requestInfo != null) {
            if (msg instanceof HttpResponse || msg instanceof HttpContent) {
                timing = requestInfo.timing();
                requestInfo.markResponseStart(timing);
            } else {
                // Raw tunnels have no HTTP end marker; retain the legacy estimate.
                requestInfo.updateResponseTime();
            }

            if (msg instanceof HttpResponse httpResponse) {
                requestInfo.updateRespSize(WebUtils.estimateSize(httpResponse));
            } else if (msg instanceof HttpContent httpContent) {
                requestInfo.updateRespSize(httpContent.content().readableBytes());
            } else {
                try {
                    ByteBuf cont = (ByteBuf) msg;
                    requestInfo.updateRespSize(cont.readableBytes());
                } catch (Exception e) {
                    log.warn("Unable to catch request size.", e);
                }
            }
        }

        ChannelFuture downstreamWrite;
        if (msg instanceof HttpResponse origin) {
            // Bug-fix: HttpAggregator removes Transfer-Encoding header
            DefaultHttpResponse copiedResp = new DefaultHttpResponse(
                    origin.protocolVersion(), origin.status(), origin.headers().copy());
            downstreamWrite = clientChannel.writeAndFlush(msg);
            ctx.fireChannelRead(copiedResp);
        } else {
            ReferenceCountUtil.retain(msg);
            downstreamWrite = clientChannel.writeAndFlush(msg);
            ctx.fireChannelRead(msg);
        }
        if (requestInfo != null && requestInfo.isRecording()
                && timing != null && msg instanceof LastHttpContent) {
            trackDownstreamCompletion(requestInfo, timing, requestInfo.getRespSize(), downstreamWrite);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent handshakeEvent) {
            logTlsHandshake(ctx, handshakeEvent);
        }
        ctx.fireUserEventTriggered(evt);
    }

    private void logTlsHandshake(ChannelHandlerContext ctx, SslHandshakeCompletionEvent event) {
        String host = ctx.channel().attr(UpstreamSslHandlerFactory.TLS_PEER_HOST).get();
        Integer port = ctx.channel().attr(UpstreamSslHandlerFactory.TLS_PEER_PORT).get();
        String sni = ctx.channel().attr(UpstreamSslHandlerFactory.TLS_SNI_HOST).get();
        Long startedAt = ctx.channel().attr(UpstreamSslHandlerFactory.TLS_HANDSHAKE_START_NANOS).get();
        long elapsedMillis = startedAt == null ? -1L
                : TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAt));

        if (event.isSuccess()) {
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            String protocol = sslHandler == null ? "-" : sslHandler.engine().getSession().getProtocol();
            String cipher = sslHandler == null ? "-" : sslHandler.engine().getSession().getCipherSuite();
            log.debug("Upstream TLS handshake completed for {}:{}, SNI={}, protocol={}, cipher={}, time={} ms",
                    host, port, sni == null ? "<none>" : sni, protocol, cipher, elapsedMillis);
            return;
        }

        tlsFailureLogged = true;
        Throwable cause = event.cause();
        log.warn("Upstream TLS handshake failed for {}:{}, SNI={}, time={} ms: {}",
                host, port, sni == null ? "<none>" : sni, elapsedMillis,
                cause == null ? "unknown error" : cause.getMessage());
        if (cause != null) {
            log.debug("Upstream TLS handshake failure", cause);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (terminalErrorHandled) {
            log.debug("Ignoring duplicate upstream client error", cause);
            ctx.close();
            return;
        }
        terminalErrorHandled = true;

        ClientStatus.Status targetStatus;

        SSLHandshakeException handshakeException = findCause(cause, SSLHandshakeException.class);
        SocketException socketException = findCause(cause, SocketException.class);
        if (handshakeException != null) {
            targetStatus = ClientStatus.Status.SSL_HANDSHAKE_ERR;
            if (!tlsFailureLogged) {
                log.warn("Upstream TLS handshake failed: {}", handshakeException.getMessage());
                log.debug("Upstream TLS handshake failure", cause);
            }
        } else if (socketException != null) {
            log.warn("Upstream client socket error: {}", socketException.getMessage());
            log.debug("Upstream client socket failure", cause);
            targetStatus = ClientStatus.Status.CONNECT_ERR;
        } else {
            log.error("Error occurred in proxy client.", cause);
            targetStatus = ClientStatus.Status.UNKNOWN_ERR;
        }

        // HttpResponse response = new DefaultFullHttpResponse(
        //         HttpVersion.HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT);
        // clientChannel.writeAndFlush(response);

        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();
        if (requestInfo != null) {
            requestInfo.updateClientStatus(targetStatus, cause.getMessage());
            ProxyRequestTiming timing = requestInfo.timing();
            requestInfo.markRequestEnd(timing);
            requestInfo.markResponseEnd(timing);
            ResponseMessage responseMsg = new ResponseMessage();
            responseMsg.setRequestId(requestInfo.getRequestId());
            responseMsg.setStartTime(timing.getResponseStartTime());
            responseMsg.setEndTime(timing.getResponseEndTime());
            responseMsg.setDurationNanos(timing.getResponseDurationNanos());
            responseMsg.setWaitingDurationNanos(timing.getWaitingDurationNanos());
            responseMsg.setSize(0);
            responseMsg.setStatus(-1);
            responseMsg.setReasonPhrase(targetStatus.getDesc());
            messageQueue.pushMsg(Topic.RECORD, responseMsg);
        }

        if (ctx.channel().isOpen()) {
            ctx.close();
        }
        if (clientChannel.isOpen()) {
            clientChannel.close();
        }
    }

    private static <T extends Throwable> T findCause(Throwable cause, Class<T> type) {
        Throwable current = cause;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private void trackDownstreamCompletion(ProxyRequestInfo requestInfo, ProxyRequestTiming timing,
                                           long responseSize, ChannelFuture writeFuture) {
        writeFuture.addListener(future -> {
            requestInfo.markResponseEnd(timing);
            ResponseMessage update = new ResponseMessage();
            update.setRequestId(timing.getRequestId());
            update.setType(BaseMessage.MessageType.UPDATE);
            update.setStartTime(timing.getResponseStartTime());
            update.setEndTime(timing.getResponseEndTime());
            update.setDurationNanos(timing.getResponseDurationNanos());
            update.setWaitingDurationNanos(timing.getWaitingDurationNanos());
            update.setSize(responseSize);

            if (!future.isSuccess()) {
                String failureMessage = future.cause() == null
                        ? "Downstream response write failed"
                        : future.cause().getMessage();
                requestInfo.updateClientStatus(ClientStatus.Status.UNKNOWN_ERR, failureMessage);
                update.setStatus(-1);
                update.setReasonPhrase(ClientStatus.Status.UNKNOWN_ERR.getDesc());

                RequestMessage requestUpdate = new RequestMessage();
                requestUpdate.setRequestId(timing.getRequestId());
                requestUpdate.setType(BaseMessage.MessageType.UPDATE);
                requestUpdate.setClientStatus(requestInfo.getClientStatus());
                messageQueue.pushMsg(Topic.UPDATE_MSG, requestUpdate);
                log.debug("Downstream response write failed for request {}", timing.getRequestId(), future.cause());
            }
            messageQueue.pushMsg(Topic.UPDATE_MSG, update);
        });
    }
}
