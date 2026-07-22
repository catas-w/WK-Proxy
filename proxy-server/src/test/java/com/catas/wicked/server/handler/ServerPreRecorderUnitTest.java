package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.server.ServerPreRecorder;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ServerPreRecorderUnitTest {

    @Test
    public void recordsRequestMetadataAndFinalSizeUpdate() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ServerPreRecorder(null, queue));
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo(true));

        DefaultHttpRequest request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "http://example.test/items");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);

        channel.writeInbound(request);
        channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(body)));
        channel.writeInbound(new DefaultLastHttpContent());

        List<CapturingMessageQueue.CapturedMessage> messages = queue.getMessages();
        Assert.assertEquals(2, messages.size());
        Assert.assertEquals(Topic.RECORD, messages.get(0).topic());
        RequestMessage recorded = (RequestMessage) messages.get(0).message();
        Assert.assertEquals("request-1", recorded.getRequestId());
        Assert.assertEquals("POST", recorded.getMethod());
        Assert.assertEquals("http://example.test/items", recorded.getRequestUrl());
        Assert.assertEquals("text/plain", recorded.getHeaders().get(HttpHeaderNames.CONTENT_TYPE.toString()));
        Assert.assertEquals(99L, recorded.getProcessInfo().getOwnerPid());

        Assert.assertEquals(Topic.UPDATE_MSG, messages.get(1).topic());
        RequestMessage update = (RequestMessage) messages.get(1).message();
        Assert.assertEquals(BaseMessage.MessageType.UPDATE, update.getType());
        Assert.assertEquals("request-1", update.getRequestId());
        Assert.assertTrue(update.getSize() >= body.length);

        channel.finishAndReleaseAll();
    }

    @Test
    public void doesNotPublishWhenRecordingIsDisabled() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ServerPreRecorder(null, queue));
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo(false));

        channel.writeInbound(new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "http://example.test/health"));

        Assert.assertTrue(queue.getMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    public void forwardsWhenRequestInfoIsUnavailable() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ServerPreRecorder(null, queue));
        DefaultHttpRequest request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "http://example.test/health");

        Assert.assertTrue(channel.writeInbound(request));
        Assert.assertSame(request, channel.readInbound());
        Assert.assertTrue(queue.getMessages().isEmpty());

        channel.finishAndReleaseAll();
    }

    private ProxyRequestInfo requestInfo(boolean recording) {
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("request-1");
        requestInfo.setHost("example.test");
        requestInfo.setPort(80);
        requestInfo.setRecording(recording);
        requestInfo.setProcessInfo(ProcessInfo.builder()
                .ownerPid(99)
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build());
        return requestInfo;
    }
}
