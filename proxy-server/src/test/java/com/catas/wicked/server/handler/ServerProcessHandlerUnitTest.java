package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.server.handler.server.ServerProcessHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

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
}
