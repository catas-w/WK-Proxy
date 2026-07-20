package com.catas.wicked.server.handler;

import com.catas.wicked.common.constant.ProxyConstant;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class RearHttpAggregatorUnitTest {

    @Test
    public void aggregatesContentWithinTheConfiguredLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(new RearHttpAggregator(16));
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/items");
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 5);

        channel.writeInbound(request);
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8)));
        channel.writeInbound(io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT);

        FullHttpRequest aggregated = channel.readInbound();
        Assert.assertEquals("hello", aggregated.content().toString(CharsetUtil.UTF_8));
        aggregated.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void emitsAnOversizeMarkerInsteadOfTheOriginalBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new RearHttpAggregator(4));
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 5);

        channel.writeInbound(request);

        FullHttpRequest oversized = channel.readInbound();
        Assert.assertTrue(oversized instanceof RearHttpAggregator.OversizeHttpRequest);
        Assert.assertEquals(ProxyConstant.OVERSIZE_MSG, oversized.content().toString(CharsetUtil.UTF_8));
        oversized.release();
        channel.finishAndReleaseAll();
    }
}
