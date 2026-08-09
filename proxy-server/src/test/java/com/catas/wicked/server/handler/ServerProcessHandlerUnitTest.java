package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.server.ServerProcessHandler;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;

public class ServerProcessHandlerUnitTest {

    @Test
    public void forwardsUnexpectedInitialPayloadToThePostProcessor() {
        EmbeddedChannel channel = new EmbeddedChannel(new ServerProcessHandler(null, null, null));
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("request-1");
        requestInfo.setClientType(ProxyRequestInfo.ClientType.NORMAL);
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo);
        ByteBuf payload = Unpooled.buffer().writeByte(1);

        Assert.assertTrue(channel.writeInbound(payload));
        Assert.assertSame(payload, channel.readInbound());
        Assert.assertEquals(1, payload.refCnt());

        payload.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void recordsNestedSslHandshakeFailureAsTerminalResponse() {
        CapturingMessageQueue messageQueue = new CapturingMessageQueue();
        EmbeddedChannel channel = new EmbeddedChannel(new ServerProcessHandler(null, messageQueue, null));
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("request-tls-failure");
        requestInfo.setHost("legacy.example");
        requestInfo.setPort(443);
        requestInfo.setSsl(true);
        requestInfo.setRecording(true);
        requestInfo.setClientType(ProxyRequestInfo.ClientType.NORMAL);
        channel.attr(AttributeKey.<ProxyRequestInfo>valueOf(ProxyConstant.REQUEST_INFO)).set(requestInfo);

        channel.pipeline().fireExceptionCaught(new DecoderException(
                new SSLHandshakeException("UNSUPPORTED_PROTOCOL")));
        channel.runPendingTasks();

        Assert.assertFalse(channel.isOpen());
        Assert.assertEquals(ClientStatus.Status.SSL_HANDSHAKE_ERR,
                requestInfo.getClientStatus().getStatus());
        Assert.assertEquals(2, messageQueue.getMessages().size());
        Assert.assertEquals(Topic.RECORD, messageQueue.getMessages().get(0).topic());
        Assert.assertTrue(messageQueue.getMessages().get(0).message() instanceof RequestMessage);
        Assert.assertTrue(((RequestMessage) messageQueue.getMessages().get(0).message()).isEncrypted());
        Assert.assertEquals(Topic.RECORD, messageQueue.getMessages().get(1).topic());
        Assert.assertTrue(messageQueue.getMessages().get(1).message() instanceof ResponseMessage);
        Assert.assertEquals(-1,
                ((ResponseMessage) messageQueue.getMessages().get(1).message()).getStatus().intValue());

        channel.finishAndReleaseAll();
    }
}
