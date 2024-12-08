package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.WebUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
public class ServerPreRecorder extends ChannelDuplexHandler {

    private final ApplicationConfig appConfig;

    private final MessageQueue messageQueue;

    private final AttributeKey<ProxyRequestInfo> requestInfoAttributeKey =
            AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    private AtomicReference<String> curRequestId;

    public ServerPreRecorder(ApplicationConfig appConfig, MessageQueue messageQueue) {
        this.appConfig = appConfig;
        this.messageQueue = messageQueue;
        curRequestId = new AtomicReference<>("initId");
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoAttributeKey).get();

        boolean newRequest = false;
        String reqId = curRequestId.get();
        if (!StringUtils.equals(reqId, requestInfo.getRequestId())) {
            newRequest = curRequestId.compareAndSet(reqId, requestInfo.getRequestId());
        }

        requestInfo.updateRequestTime();
        if (msg instanceof HttpRequest httpRequest) {
            requestInfo.updateRequestSize(WebUtils.estimateSize(httpRequest));
            // requestInfo.updateRequestSize(((ByteBuf) msg).readableBytes());
            recordHttpRequest(requestInfo, httpRequest, newRequest);
        } else if (msg instanceof HttpContent content) {
            requestInfo.updateRequestSize(content.content().readableBytes());
            recordHttpRequest(requestInfo, content, newRequest);
        } else {
            try {
                ByteBuf cont = (ByteBuf) msg;
                requestInfo.updateRequestSize(cont.readableBytes());
            } catch (Exception e) {
                log.warn("Unable to catch request size.", e);
            }
            recordHttpRequest(requestInfo, msg, newRequest);
        }

        super.channelRead(ctx, msg);
    }

    /**
     * record parsed httpRequest
     */
    private void recordHttpRequest(ProxyRequestInfo requestInfo, HttpRequest request, boolean newRequest) {
        if (!requestInfo.isRecording()) {
            return;
        }

        String uri = request.uri();
        HttpHeaders headers = request.headers();
        HttpMethod method = request.method();

        uri = WebUtils.completeUri(uri, requestInfo);
        RequestMessage requestMessage = new RequestMessage(uri);
        Map<String, String> headerMap = new LinkedHashMap<>();
        headers.entries().forEach(entry -> {
            headerMap.put(entry.getKey(), entry.getValue());
        });
        requestMessage.setMethod(method.name());
        requestMessage.setHeaders(headerMap);
        requestMessage.setProtocol(request.protocolVersion().text());
        setRequestMsgInfo(requestInfo, requestMessage);

        messageQueue.pushMsg(Topic.RECORD, requestMessage);

        requestInfo.setHasSentRequestMsg(true);
        log.info(">>>> Request Prev-Send[decoded]: {} ID: {} >>>>", uri, requestInfo.getRequestId());
    }

    /**
     * record lastHttpContent
     */
    private void recordHttpRequest(ProxyRequestInfo requestInfo, HttpContent httpContent, boolean newRequest) {
        if (!requestInfo.isRecording()) {
            return;
        }
        // update trailing headers and size & timing
        if (httpContent instanceof LastHttpContent lastHttpContent) {
            RequestMessage requestMessage = new RequestMessage();
            requestMessage.setRequestId(requestInfo.getRequestId());
            requestMessage.setType(BaseMessage.MessageType.UPDATE);
            requestMessage.setEndTime(requestInfo.getRequestEndTime());
            requestMessage.setSize(requestInfo.getRequestSize());

            LinkedHashMap<String, String> headerMap = new LinkedHashMap<>();
            HttpHeaders trailingHeaders = lastHttpContent.trailingHeaders();
            trailingHeaders.entries().forEach(entry -> headerMap.put(entry.getKey(), entry.getValue()));
            requestMessage.setHeaders(headerMap);
            messageQueue.pushMsg(Topic.UPDATE_MSG, requestMessage);
        }
    }

    /**
     * record unparsed httpRequest
     */
    private void recordHttpRequest(ProxyRequestInfo requestInfo, Object msg, boolean newRequest) {
        if (!requestInfo.isRecording()) {
            return;
        }

        if (newRequest) {
            String url = WebUtils.getHostname(requestInfo) + "/" + ProxyConstant.UNPARSED_ALIAS;
            RequestMessage requestMessage = new RequestMessage(url);
            requestMessage.setMethod("-");
            requestMessage.setHeaders(new LinkedHashMap<>());
            requestMessage.setEncrypted(true);
            setRequestMsgInfo(requestInfo, requestMessage);
            messageQueue.pushMsg(Topic.RECORD, requestMessage);

            requestInfo.setHasSentRequestMsg(true);
            log.info(">>>> Request Prev-Send[encrypted]: {} ID: {} >>>>", url, requestInfo.getRequestId());
        } else {
            // update size & time
            requestInfo.updateRequestTime();
            RequestMessage requestMessage = new RequestMessage();
            requestMessage.setRequestId(requestInfo.getRequestId());
            requestMessage.setType(BaseMessage.MessageType.UPDATE);
            requestMessage.setEndTime(requestInfo.getRequestEndTime());
            requestMessage.setSize(requestInfo.getRequestSize());
            messageQueue.pushMsg(Topic.UPDATE_MSG, requestMessage);
        }
    }

    private void setRequestMsgInfo(ProxyRequestInfo requestInfo, RequestMessage requestMsg) {
        requestMsg.setRequestId(requestInfo.getRequestId());
        requestMsg.setStartTime(requestInfo.getRequestStartTime());
        requestMsg.setEndTime(requestInfo.getRequestEndTime());
        requestMsg.setSize(requestInfo.getRequestSize());
        requestMsg.setRemoteHost(requestInfo.getHost());
        requestMsg.setRemotePort(requestInfo.getPort());
        // requestMsg.setRemoteAddress(requestInfo.getRemoteAddress());
        requestMsg.setLocalAddress(requestInfo.getLocalAddress());
        requestMsg.setLocalPort(requestInfo.getLocalPort());
        // requestMsg.setClientStatus(requestInfo.getClientStatus().copy());
    }
}
