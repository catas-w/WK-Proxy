package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.client.ClientProcessHandler;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientProcessHandlerUnitTest {

    @Test
    public void recordsNestedHandshakeFailureWithoutPropagatingItAgain() {
        CapturingMessageQueue messageQueue = new CapturingMessageQueue();
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        AtomicInteger propagatedErrors = new AtomicInteger();
        EmbeddedChannel upstreamChannel = new EmbeddedChannel(
                new ClientProcessHandler(clientChannel, messageQueue),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        propagatedErrors.incrementAndGet();
                    }
                });
        ProxyRequestInfo requestInfo = requestInfo("tls-failure");
        upstreamChannel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO))
                .set(requestInfo);
        DecoderException failure = new DecoderException(
                new SSLHandshakeException("SSLV3_ALERT_HANDSHAKE_FAILURE"));

        upstreamChannel.pipeline().fireExceptionCaught(failure);
        upstreamChannel.runPendingTasks();
        clientChannel.runPendingTasks();

        Assert.assertEquals(ClientStatus.Status.SSL_HANDSHAKE_ERR,
                requestInfo.getClientStatus().getStatus());
        Assert.assertEquals(1, messageQueue.getMessages().size());
        Assert.assertEquals(Topic.RECORD, messageQueue.getMessages().get(0).topic());
        Assert.assertTrue(messageQueue.getMessages().get(0).message() instanceof ResponseMessage);
        Assert.assertEquals(-1, ((ResponseMessage) messageQueue.getMessages().get(0).message())
                .getStatus().intValue());
        Assert.assertEquals(0, propagatedErrors.get());
        Assert.assertFalse(upstreamChannel.isOpen());
        Assert.assertFalse(clientChannel.isOpen());

        upstreamChannel.finishAndReleaseAll();
        clientChannel.finishAndReleaseAll();
    }

    @Test
    public void closesSafelyWhenRequestInfoHasNotBeenAttached() {
        CapturingMessageQueue messageQueue = new CapturingMessageQueue();
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel upstreamChannel = new EmbeddedChannel(
                new ClientProcessHandler(clientChannel, messageQueue));

        upstreamChannel.pipeline().fireExceptionCaught(
                new SSLHandshakeException("handshake failed before request setup"));
        upstreamChannel.runPendingTasks();
        clientChannel.runPendingTasks();

        Assert.assertTrue(messageQueue.getMessages().isEmpty());
        Assert.assertFalse(upstreamChannel.isOpen());
        Assert.assertFalse(clientChannel.isOpen());

        upstreamChannel.finishAndReleaseAll();
        clientChannel.finishAndReleaseAll();
    }

    private static ProxyRequestInfo requestInfo(String requestId) {
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId(requestId);
        requestInfo.setHost("origin.example");
        requestInfo.setPort(443);
        requestInfo.setSsl(true);
        requestInfo.setClientType(ProxyRequestInfo.ClientType.NORMAL);
        requestInfo.updateClientStatus(ClientStatus.Status.WAITING);
        return requestInfo;
    }
}
