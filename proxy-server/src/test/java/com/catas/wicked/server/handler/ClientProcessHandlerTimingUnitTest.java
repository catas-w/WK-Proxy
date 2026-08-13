package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.client.ClientProcessHandler;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.junit.Assert;
import org.junit.Test;

public class ClientProcessHandlerTimingUnitTest {

    @Test
    public void completesResponseOnlyAfterFinalDownstreamWrite() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        DelayedLastWriteHandler delayedWrite = new DelayedLastWriteHandler();
        EmbeddedChannel downstream = new EmbeddedChannel(delayedWrite);
        EmbeddedChannel upstream = new EmbeddedChannel(new ClientProcessHandler(downstream, queue));

        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("request-1");
        requestInfo.setRecording(true);
        requestInfo.resetBasicInfo();
        requestInfo.markRequestStart();
        requestInfo.markRequestEnd(requestInfo.timing());
        upstream.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo);

        upstream.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        upstream.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[]{1, 2, 3})));

        Assert.assertTrue(queue.getMessages().isEmpty());
        Assert.assertEquals(0, requestInfo.getResponseEndTime());

        delayedWrite.completeLastWrite();

        Assert.assertEquals(1, queue.getMessages().size());
        Assert.assertEquals(Topic.UPDATE_MSG, queue.getMessages().get(0).topic());
        ResponseMessage update = (ResponseMessage) queue.getMessages().get(0).message();
        Assert.assertEquals("request-1", update.getRequestId());
        Assert.assertTrue(update.getStartTime() > 0);
        Assert.assertTrue(update.getEndTime() >= update.getStartTime());
        Assert.assertTrue(update.getDurationNanos() > 0);

        releaseAll(upstream);
        releaseAll(downstream);
        upstream.finishAndReleaseAll();
        downstream.finishAndReleaseAll();
    }

    private static void releaseAll(EmbeddedChannel channel) {
        Object message;
        while ((message = channel.readInbound()) != null) {
            ReferenceCountUtil.release(message);
        }
        while ((message = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    private static final class DelayedLastWriteHandler extends ChannelOutboundHandlerAdapter {
        private Object lastMessage;
        private ChannelPromise lastPromise;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof LastHttpContent) {
                lastMessage = msg;
                lastPromise = promise;
                return;
            }
            ctx.write(msg, promise);
        }

        private void completeLastWrite() {
            Assert.assertNotNull(lastPromise);
            ReferenceCountUtil.release(lastMessage);
            lastMessage = null;
            lastPromise.setSuccess();
            lastPromise = null;
        }
    }
}
