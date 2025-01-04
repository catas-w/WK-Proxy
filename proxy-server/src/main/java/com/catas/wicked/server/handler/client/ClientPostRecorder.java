package com.catas.wicked.server.handler.client;


import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.component.OversizeHttpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端响应记录器
 * record responses
 */
@Slf4j
public class ClientPostRecorder extends ChannelDuplexHandler {

    private ApplicationConfig appConfig;
    private MessageQueue messageQueue;
    private final AttributeKey<ProxyRequestInfo> requestInfoKey = AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    public ClientPostRecorder(ApplicationConfig applicationConfig, MessageQueue messageQueue) {
        this.appConfig = applicationConfig;
        this.messageQueue = messageQueue;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ProxyRequestInfo requestInfo = ctx.channel().attr(requestInfoKey).get();
        if (!requestInfo.isRecording()) {
            // ReferenceCountUtil.release(msg);
            ctx.fireChannelRead(msg);
            return;
        }
        if (msg instanceof FullHttpResponse response) {
            try {
                recordHttpResponse(ctx, response, requestInfo);
            } catch (Exception e) {
                log.error("Error in recording response.", e);
            }
        } else {
            // record un-parsed response
            recordUnParsedResponse(ctx, requestInfo);
            // ReferenceCountUtil.release(msg);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // ctx.channel().close();
        log.error("Error occurred in Proxy client.", cause);
    }

    private void recordUnParsedResponse(ChannelHandlerContext ctx, ProxyRequestInfo requestInfo) {
        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setRequestId(requestInfo.getRequestId());
        responseMessage.setStartTime(requestInfo.getResponseStartTime());
        responseMessage.setEndTime(requestInfo.getResponseEndTime());
        responseMessage.setSize(requestInfo.getRespSize());
        messageQueue.pushMsg(Topic.RECORD, responseMessage);

        requestInfo.setHasSentRespMsg(true);
        log.info("<<<< Response received: {} <<<<", requestInfo.getRequestId());
    }

    private void recordHttpResponse(ChannelHandlerContext ctx, FullHttpResponse resp, ProxyRequestInfo requestInfo) {
        HttpHeaders headers = resp.headers();
        HttpResponseStatus status = resp.status();

        ResponseMessage responseMessage = new ResponseMessage();
        Map<String, String> map = new LinkedHashMap<>();
        headers.entries().forEach(entry -> {
            map.put(entry.getKey(), entry.getValue());
        });

        responseMessage.setStatus(status.code());
        responseMessage.setReasonPhrase(status.reasonPhrase());
        responseMessage.setHeaders(map);
        ByteBuf content = resp.content();
        if (content != null && content.isReadable()) {
            if (content.hasArray()) {
                responseMessage.setContent(content.array());
            } else {
                byte[] bytes = new byte[content.readableBytes()];
                content.getBytes(content.readerIndex(), bytes);
                responseMessage.setContent(bytes);
            }
        }

        responseMessage.setRequestId(requestInfo.getRequestId());
        responseMessage.setStartTime(requestInfo.getResponseStartTime());
        responseMessage.setEndTime(requestInfo.getResponseEndTime());
        responseMessage.setSize(requestInfo.getRespSize());
        if (resp instanceof OversizeHttpMessage) {
            responseMessage.setOversize(true);
        }
        messageQueue.pushMsg(Topic.RECORD, responseMessage);

        requestInfo.setHasSentRespMsg(true);
        log.info("<<<< Response received: {} <<<<", requestInfo.getRequestId());
    }
}
