package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.IdGenerator;
import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.constant.ConnectionStatus;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.common.util.AntMatcherUtils;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.server.strategy.Handler;
import com.catas.wicked.server.strategy.StrategyList;
import com.catas.wicked.server.strategy.StrategyManager;
import io.micronaut.core.util.CollectionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.NoSuchElementException;


/**
 * Decide which handlers to use for current channel and refresh request-info
 * ---
 * Http: 全部解码
 * Https: 仅在 isRecord && handleSSl && certOK 时解码，其他情况均发送原始数据块
 * ---
 * http record [NORMAL]:    httpCodec - strategyHandler - proxyProcessHandler - [aggregator] - postRecorder
 * http un-record [NORMAL]: httpCodec - strategyHandler - proxyProcessHandler - postRecorder
 * ssl record [NORMAL]:     [sslHandler] - httpCodec - strategyHandler - proxyProcessHandler - [aggregator] - postRecorder
 * ssl record [TUNNEL]:     strategyHandler - proxyProcessHandler - postRecorder
 * ssl un-record [TUNNEL]:  strategyHandler - proxyProcessHandler - postRecorder
 */
@Slf4j
// @ChannelHandler.Sharable
public class ServerStrategyHandler extends ChannelDuplexHandler {

    private byte[] httpTagBuf;

    private final ApplicationConfig appConfig;

    private ConnectionStatus status;

    // private final CertPool certPool;

    private final CertManager certManager;

    private IdGenerator idGenerator;

    private StrategyList strategyList;

    private StrategyManager strategyManager;

    private final AttributeKey<ProxyRequestInfo> requestInfoAttributeKey = AttributeKey.valueOf("requestInfo");

    public ServerStrategyHandler(ApplicationConfig applicationConfig,
                                 CertManager certManager,
                                 IdGenerator idGenerator,
                                 StrategyList strategyList,
                                 StrategyManager strategyManager) {
        this.appConfig = applicationConfig;
        // this.certPool = certPool;
        this.certManager = certManager;
        this.status = ConnectionStatus.INIT;
        this.idGenerator = idGenerator;
        this.strategyList = strategyList;
        this.strategyManager = strategyManager;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Bug-fix: HttpAggregator removes Transfer-Encoding header
        // if (msg instanceof HttpMessage httpMessage) {
        //     HttpHeaders headers = httpMessage.headers();
        //     log.info("Resp headers: {}", headers.entries().size());
        // }
        super.write(ctx, msg, promise);
        if (msg instanceof HttpResponse httpResponse) {
            if (HttpHeaderValues.WEBSOCKET.toString().equals(httpResponse.headers().get(HttpHeaderNames.UPGRADE))){
                // remove httpCodec in websocket
                strategyList.setRequire(Handler.HTTP_CODEC.name(), false);
                strategyManager.arrange(ctx.channel().pipeline(), strategyList);
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, msg);
        } else if (msg instanceof HttpContent) {
            // 处理 head 后的 LastContent
            if (status == ConnectionStatus.IN_CONNECT) {
                status = ConnectionStatus.AFTER_CONNECT;
                ReferenceCountUtil.release(msg);
                return;
            }
            ctx.fireChannelRead(msg);
        } else if (!(msg instanceof HttpObject)) {
            handleRaw(ctx, msg);
        }
    }

    /**
     * Refresh http request-info when new request arrives
     */
    private ProxyRequestInfo refreshRequestInfo(ChannelHandlerContext ctx, HttpRequest request) {
        Attribute<ProxyRequestInfo> attr = ctx.channel().attr(requestInfoAttributeKey);
        ProxyRequestInfo requestInfo = attr.get();
        if (requestInfo == null && request != null) {
            requestInfo = WebUtils.getRequestProto(request);
            attr.set(requestInfo);
        }
        assert requestInfo != null;
        requestInfo.setUsingExternalProxy(appConfig.getSettings().getExternalProxy() != null &&
                appConfig.getSettings().isEnableExProxy());
        requestInfo.setRequestId(idGenerator.nextId());
        // requestInfo.setRecording(appConfig.getSettings().isRecording());
        requestInfo.setRecording(needRecord(appConfig, requestInfo, request));
        requestInfo.setThrottling(appConfig.getSettings().isThrottle());
        requestInfo.updateClientStatus(ClientStatus.Status.WAITING);
        requestInfo.resetBasicInfo();

        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        SocketAddress localAddress = ctx.channel().localAddress();
        if (remoteAddress instanceof InetSocketAddress inetRemoteAddress) {
            requestInfo.setLocalAddress(inetRemoteAddress.getAddress().getHostAddress());
            requestInfo.setLocalPort(inetRemoteAddress.getPort());
        }
        // if (localAddress instanceof InetSocketAddress inetLocalAddress) {
        //     requestInfo.setLocalAddress(inetLocalAddress.getAddress().getHostAddress());
        //     requestInfo.setLocalPort(inetLocalAddress.getPort());
        // }
        return requestInfo;
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, Object msg) {
        HttpRequest request = (HttpRequest) msg;
        DecoderResult result = request.decoderResult();
        Throwable cause = result.cause();

        if (cause instanceof DecoderException) {
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            ctx.writeAndFlush(response);
            ReferenceCountUtil.release(msg);
            return;
        }

        ProxyRequestInfo requestInfo = refreshRequestInfo(ctx, request);
        strategyList.setRequire(Handler.HTTP_AGGREGATOR.name(), requestInfo.isRecording());
        strategyList.setRequire(Handler.THROTTLE_HANDLER.name(), requestInfo.isThrottling());
        if (requestInfo.isThrottling()) {
            strategyList.setSupplier(Handler.THROTTLE_HANDLER.name(), () -> getThrottleHandler(appConfig));
        }
        strategyManager.arrange(ctx.pipeline(), strategyList);

        requestInfo.setClientType(ProxyRequestInfo.ClientType.NORMAL);
        // attr.set(requestInfo);

        if (status == ConnectionStatus.INIT || status == ConnectionStatus.AFTER_CONNECT) {
            if (HttpMethod.CONNECT.name().equalsIgnoreCase(request.method().name())) {
                status = ConnectionStatus.IN_CONNECT;
                // https connect
                HttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), ProxyConstant.SUCCESS);
                ctx.writeAndFlush(response);
                strategyList.setRequire(Handler.HTTP_CODEC.name(), false);
                strategyManager.arrange(ctx.pipeline(), strategyList);

                ReferenceCountUtil.release(msg);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void handleRaw(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();

        if (byteBuf.getByte(0) == 22 && status == ConnectionStatus.AFTER_CONNECT) {
            // process new request
            requestInfo.setSsl(true);
            if (requestInfo.isRecording() && needHandlerSsl(appConfig, requestInfo)) {
                int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
                String originHost = requestInfo.getHost();
                SslContext sslCtx = SslContextBuilder.forServer(
                        appConfig.getServerPriKey(), certManager.getServerCert(port, originHost)).build();
                strategyList.setRequire(Handler.HTTP_CODEC.name(), true);
                strategyList.setRequire(Handler.SSL_HANDLER.name(), true);
                strategyList.setSupplier(Handler.SSL_HANDLER.name(), () -> sslCtx.newHandler(ctx.alloc()));
                strategyManager.arrange(ctx.pipeline(), strategyList);
                ctx.pipeline().fireChannelRead(msg);
                return;
            }
        }

        if (requestInfo.getClientType() == ProxyRequestInfo.ClientType.NORMAL) {
            requestInfo.setClientType(ProxyRequestInfo.ClientType.TUNNEL);
            try {
                // ctx.pipeline().remove(AGGREGATOR);
                strategyList.setRequire(Handler.HTTP_CODEC.name(), false);
                strategyList.setRequire(Handler.HTTP_AGGREGATOR.name(), false);
                strategyManager.arrange(ctx.pipeline(), strategyList);
            } catch (NoSuchElementException ignore) {}
        }
        if (byteBuf.readableBytes() < 8) {
            httpTagBuf = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(httpTagBuf);
            ReferenceCountUtil.release(msg);
            return;
        }
        if (httpTagBuf != null) {
            byte[] tmp = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(tmp);
            byteBuf.writeBytes(httpTagBuf);
            byteBuf.writeBytes(tmp);
            httpTagBuf = null;
        }

        // 如果connect后面跑的是HTTP报文，也可以抓包处理
        if (WebUtils.isHttp(byteBuf)) {
            strategyList.setRequire(Handler.HTTP_CODEC.name(), true);
            strategyManager.arrange(ctx.pipeline(), strategyList);

            ctx.pipeline().fireChannelRead(msg);
            return;
        }
        ctx.fireChannelRead(msg);
    }

    private boolean needRecord(ApplicationConfig appConfig, ProxyRequestInfo requestInfo, HttpRequest request) {
        Settings settings = appConfig.getSettings();
        if (!settings.isRecording()) {
            return false;
        }
        if (HttpMethod.CONNECT.name().equalsIgnoreCase(request.method().name())) {
            return true;
        }

        boolean res = true;
        String uri = request.uri();
        uri = WebUtils.completeUri(uri, requestInfo);
        // if (CollectionUtils.isNotEmpty(settings.getRecordIncludeList())) {
        //     res = AntMatcherUtils.matches(settings.getRecordIncludeList(), uri);
        // }
        if (CollectionUtils.isNotEmpty(settings.getRecordExcludeList())) {
            res = !AntMatcherUtils.matchAny(settings.getRecordExcludeList(), uri);
        }
        return res;
    }

    private boolean needHandlerSsl(ApplicationConfig appConfig, ProxyRequestInfo requestInfo) {
        Settings settings = appConfig.getSettings();
        if (!settings.isHandleSsl()) {
            return false;
        }

        return CollectionUtils.isEmpty(settings.getSslExcludeList())
                || !AntMatcherUtils.matchAny(settings.getSslExcludeList(), requestInfo.getHost());
    }

    private ChannelTrafficShapingHandler getThrottleHandler(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        ThrottlePreset preset = settings.getThrottlePreset();
        if (preset == null) {
            preset = ThrottlePreset.SLOW_2G;
        }
        return new ChannelTrafficShapingHandler(preset.getWriteLimit(), preset.getReadLimit(),
                preset.getCheckInterval(), preset.getMaxTime());
    }
}
