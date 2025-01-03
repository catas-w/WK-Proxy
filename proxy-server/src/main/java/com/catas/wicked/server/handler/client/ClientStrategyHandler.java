package com.catas.wicked.server.handler.client;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.server.strategy.Handler;
import com.catas.wicked.server.strategy.StrategyList;
import com.catas.wicked.server.strategy.StrategyManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;

/**
 * decide which handlers to use when sending new request
 * remote <-<-<- local
 * [Normal]: [externalProxyHandler] - [sslHandler] - [httpCodec] - proxyClientHandler -
 *           [aggregator] - postRecorder - strategyHandler
 * [Tunnel]: [externalProxyHandler] - proxyClientHandler - postRecorder - strategyHandler
 */
@Slf4j
public class ClientStrategyHandler extends ChannelDuplexHandler {

    private ApplicationConfig appConfig;

    private ProxyRequestInfo requestInfo;

    private MessageQueue messageQueue;

    private String currentRequestId;

    private StrategyList strategyList;

    private StrategyManager strategyManager;

    private final AttributeKey<ProxyRequestInfo> requestInfoKey = AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    public ClientStrategyHandler(ApplicationConfig appConfig,
                                 MessageQueue messageQueue,
                                 ProxyRequestInfo requestInfo,
                                 StrategyList strategyList,
                                 StrategyManager strategyManager) {
        this.appConfig = appConfig;
        this.requestInfo = requestInfo;
        this.messageQueue = messageQueue;
        this.strategyList = strategyList;
        this.strategyManager = strategyManager;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().hasAttr(requestInfoKey) && ctx.channel().attr(requestInfoKey).get() != null) {
            // log.info("** Client inactive: {} **", ctx.channel().attr(requestInfoKey).get().getRequestId());
            ctx.channel().attr(requestInfoKey).get().setClientConnected(true);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        super.read(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        super.flush(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Before Arrange: [HttpProxyHandler$HttpClientCodecWrapper#0, EXTERNAL_PROXY, CLIENT_PROCESSOR, POST_RECORDER
        // , CLIENT_STRATEGY, DefaultChannelPipeline$TailContext#0]
        refreshStrategy(ctx);
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel().attr(requestInfoKey).get() != null) {
            ctx.channel().attr(requestInfoKey).get().updateResponseTime();
        }
        if (msg instanceof HttpResponse httpResponse) {
            // remove httpCodec in websocket
            if (HttpHeaderValues.WEBSOCKET.toString().equals(httpResponse.headers().get(HttpHeaderNames.UPGRADE))){
                strategyList.setRequire(Handler.HTTP_CODEC.name(), false);
                strategyManager.arrange(ctx.channel().pipeline(), strategyList);
            }
        }
        ReferenceCountUtil.release(msg);
    }

    public void refreshStrategy(ChannelHandlerContext ctx) throws Exception {
        if (!StringUtils.equals(currentRequestId, requestInfo.getRequestId())) {
            // new response
            if (!ctx.channel().hasAttr(requestInfoKey)) {
                ctx.channel().attr(requestInfoKey).set(requestInfo);
            }
            currentRequestId = requestInfo.getRequestId();
            Channel ch = ctx.channel();

            // update record strategy
            if (requestInfo.getClientType() == ProxyRequestInfo.ClientType.NORMAL) {
                // update sslHandler
                if (requestInfo.isSsl()) {
                    try {
                        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
                        SslHandler sslHandler = appConfig.getClientSslCtx().newHandler(ch.alloc(), address.getHostName(), address.getPort());
                        strategyList.setSupplier(Handler.SSL_HANDLER.name(), () -> sslHandler);
                        strategyList.setRequire(Handler.SSL_HANDLER.name(), true);
                    } catch (Exception e) {
                        log.error("Error establish Ssl context", e);
                        strategyList.setRequire(Handler.SSL_HANDLER.name(), false);
                    }
                } else {
                    strategyList.setRequire(Handler.SSL_HANDLER.name(), false);
                }

                strategyList.setRequire(Handler.HTTP_CODEC.name(), true);
                strategyList.setRequire(Handler.HTTP_AGGREGATOR.name(), requestInfo.isRecording());
            } else {
                strategyList.setRequire(Handler.HTTP_CODEC.name(), false);
                strategyList.setRequire(Handler.HTTP_AGGREGATOR.name(), false);
                strategyList.setRequire(Handler.SSL_HANDLER.name(), false);
            }
            strategyManager.arrange(ch.pipeline(), strategyList);
        }
        log.info(">> Client send data: {} >>", requestInfo.getRequestId());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Client channel unexpected error, closing...", cause);
        ctx.channel().close();
    }
}
