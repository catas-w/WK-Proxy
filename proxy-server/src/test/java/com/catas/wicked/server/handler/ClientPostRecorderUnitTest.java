package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.client.ClientPostRecorder;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ClientPostRecorderUnitTest {

    @Test
    public void recordsOnlyTheReadableRegionOfAResponseBody() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ClientPostRecorder(null, queue));
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo(true));

        byte[] storage = "xxpayloadyy".getBytes(StandardCharsets.UTF_8);
        ByteBuf body = Unpooled.wrappedBuffer(storage, 2, 7).slice();
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");

        channel.writeInbound(response);

        Assert.assertEquals(1, queue.getMessages().size());
        Assert.assertEquals(Topic.RECORD, queue.getMessages().get(0).topic());
        ResponseMessage recorded = (ResponseMessage) queue.getMessages().get(0).message();
        Assert.assertEquals("request-1", recorded.getRequestId());
        Assert.assertEquals(Integer.valueOf(201), recorded.getStatus());
        Assert.assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), recorded.getContent());
        Assert.assertEquals("text/plain", recorded.getHeaders().get(HttpHeaderNames.CONTENT_TYPE.toString()));

        channel.finishAndReleaseAll();
    }

    @Test
    public void forwardsWithoutPublishingWhenRecordingIsDisabled() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ClientPostRecorder(null, queue));
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo(false));

        channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT));

        Assert.assertTrue(queue.getMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    private ProxyRequestInfo requestInfo(boolean recording) {
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("request-1");
        requestInfo.setRecording(recording);
        requestInfo.updateResponseTime();
        return requestInfo;
    }
}
