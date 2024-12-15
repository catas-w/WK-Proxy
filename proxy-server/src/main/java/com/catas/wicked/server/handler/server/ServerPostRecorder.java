package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.server.component.OversizeHttpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代理服务器请求记录器
 * record requests
 */
@Slf4j
public class ServerPostRecorder extends ChannelDuplexHandler {

    private ApplicationConfig appConfig;
    private MessageQueue messageQueue;
    private final AttributeKey<ProxyRequestInfo> requestInfoKey = AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    public ServerPostRecorder(ApplicationConfig applicationConfig, MessageQueue messageQueue) {
        this.appConfig = applicationConfig;
        this.messageQueue = messageQueue;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // update response size & time
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoKey).get();
        if (requestInfo != null && requestInfo.isHasSentRespMsg()) {
            requestInfo.updateResponseTime();

            ResponseMessage responseMsg = new ResponseMessage();
            responseMsg.setRequestId(requestInfo.getRequestId());
            responseMsg.setType(BaseMessage.MessageType.UPDATE);
            responseMsg.setEndTime(requestInfo.getResponseEndTime());
            responseMsg.setSize(requestInfo.getRespSize());
            messageQueue.pushMsg(Topic.UPDATE_MSG, responseMsg);
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoKey).get();
        if (!requestInfo.isRecording()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
            if (msg instanceof FullHttpRequest fullHttpRequest) {
                recordHttpRequest(ctx, fullHttpRequest, requestInfo);
            } else if (!(msg instanceof HttpObject)) {
                recordUnParsedRequest(ctx, msg, requestInfo);
            } else {
                log.error("Unknown msg type: {}", msg);
            }
        } catch (Exception e) {
            log.error("Error in recording http request.", e);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void setRequestMsgInfo(ProxyRequestInfo requestInfo, RequestMessage requestMsg) {
        requestMsg.setRequestId(requestInfo.getRequestId());
        requestMsg.setStartTime(requestInfo.getRequestStartTime());
        requestMsg.setEndTime(requestInfo.getRequestEndTime());
        requestMsg.setSize(requestInfo.getRequestSize());
        requestMsg.setRemoteHost(requestInfo.getHost());
        requestMsg.setRemotePort(requestInfo.getPort());
        requestMsg.setRemoteAddress(requestInfo.getRemoteAddress());
        requestMsg.setLocalAddress(requestInfo.getLocalAddress());
        requestMsg.setLocalPort(requestInfo.getLocalPort());
        requestMsg.setClientStatus(requestInfo.getClientStatus().copy());
    }

    /**
     * record unparsed http request
     */
    private void recordUnParsedRequest(ChannelHandlerContext ctx, Object msg, ProxyRequestInfo requestInfo) {
        if (!requestInfo.isNewAndReset()) {
            return;
        }

        String url = WebUtils.getHostname(requestInfo) + "/" + ProxyConstant.UNPARSED_ALIAS;
        RequestMessage requestMessage = new RequestMessage(url);
        requestMessage.setMethod("UNKNOWN");
        requestMessage.setHeaders(new LinkedHashMap<>());
        requestMessage.setEncrypted(true);
        setRequestMsgInfo(requestInfo, requestMessage);
        messageQueue.pushMsg(Topic.UPDATE_MSG, requestMessage);

        requestInfo.setHasSentRequestMsg(true);
        log.info(">>>> Request send[encrypted]: {} ID: {} >>>>", url, requestInfo.getRequestId());
    }

    /**
     * decode HttpPostRequestDecoder
     * 记录请求信息
     */
    private void recordHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request, ProxyRequestInfo requestInfo) {
        String uri = request.uri();
        HttpHeaders headers = request.headers();
        HttpMethod method = request.method();
        // log.info("-- uri: {}\n-- headers: {}\n-- method: {}", uri, headers, method);
        ByteBuf content = request.content();

        uri = WebUtils.completeUri(uri, requestInfo);
        RequestMessage requestMessage = new RequestMessage(uri);
        Map<String, String> headerMap = new LinkedHashMap<>();
        headers.entries().forEach(entry -> {
            headerMap.put(entry.getKey(), entry.getValue());
        });
        requestMessage.setMethod(method.name());
        requestMessage.setHeaders(headerMap);

        try {
            if (content != null && content.isReadable()) {
                if (content.hasArray()) {
                    requestMessage.setBody(content.array());
                } else {
                    byte[] bytes = new byte[content.readableBytes()];
                    content.getBytes(content.readerIndex(), bytes);
                    requestMessage.setBody(bytes);
                }
            }
        } catch (Exception e) {
            log.error("Error recording request content.", e);
        }

        // save to request tree
        requestMessage.setProtocol(request.protocolVersion().text());
        if (request instanceof OversizeHttpMessage) {
            requestMessage.setOversize(true);
        }
        setRequestMsgInfo(requestInfo, requestMessage);
        messageQueue.pushMsg(Topic.UPDATE_MSG, requestMessage);

        requestInfo.setHasSentRequestMsg(true);
        log.info(">>>> Request send[decoded]: {} ID: {} >>>>", uri, requestInfo.getRequestId());
    }
}
